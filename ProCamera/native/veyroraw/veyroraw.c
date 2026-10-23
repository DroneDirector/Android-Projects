/*
 * AlcorCut Pro's small bridge to LibRaw (used for camera RAW formats other than DNG: Sony ARW,
 * Canon CR2/CR3, Nikon NEF, Fujifilm RAF, Olympus ORF, Panasonic RW2, Pentax PEF…).
 *
 * LibRaw itself is a separate shared library (libraw.so, LGPL 2.1) used only through its public C
 * API, so it can be replaced by the user. This file is AlcorCut Pro's own code.
 *
 * Everything else (colour, white balance, demosaic, lens corrections, editing) is done by the app's
 * own RAW pipeline: LibRaw only reads the file — the sensor data, its colour pattern, black / white
 * levels, the camera's colour matrix and white balance, and the embedded preview.
 */
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "libraw/libraw.h"

#define FN(n) Java_com_dronedirector_veyrocutpro_editor_LibRawBridge_##n
#define H(h) ((libraw_data_t *)(intptr_t)(h))

JNIEXPORT jlong JNICALL FN(nOpen)(JNIEnv *e, jclass c, jstring path) {
    const char *p = (*e)->GetStringUTFChars(e, path, 0);
    libraw_data_t *d = libraw_init(LIBRAW_OPIONS_NO_DATAERR_CALLBACK);
    if (!d) { (*e)->ReleaseStringUTFChars(e, path, p); return -1; }
    int r = libraw_open_file(d, p);
    (*e)->ReleaseStringUTFChars(e, path, p);
    if (r != LIBRAW_SUCCESS) { libraw_close(d); return r < 0 ? r : -1; } /* negative: LibRaw's error code */
    return (jlong)(intptr_t)d;
}

JNIEXPORT void JNICALL FN(nClose)(JNIEnv *e, jclass c, jlong h) { if (h) libraw_close(H(h)); }

JNIEXPORT jint JNICALL FN(nUnpack)(JNIEnv *e, jclass c, jlong h) { return libraw_unpack(H(h)); }

static jstring str(JNIEnv *e, const char *s) { return (*e)->NewStringUTF(e, s ? s : ""); }

/** make, model, lens, colour description ("RGBG"). */
JNIEXPORT jobjectArray JNICALL FN(nStrings)(JNIEnv *e, jclass c, jlong h) {
    libraw_data_t *d = H(h);
    jobjectArray a = (*e)->NewObjectArray(e, 4, (*e)->FindClass(e, "java/lang/String"), 0);
    (*e)->SetObjectArrayElement(e, a, 0, str(e, d->idata.make));
    (*e)->SetObjectArrayElement(e, a, 1, str(e, d->idata.model));
    (*e)->SetObjectArrayElement(e, a, 2, str(e, d->lens.Lens[0] ? d->lens.Lens : d->lens.makernotes.Lens));
    char cd[5]; memcpy(cd, d->idata.cdesc, 4); cd[4] = 0;
    (*e)->SetObjectArrayElement(e, a, 3, str(e, cd));
    return a;
}

/*
 * Numbers: 0 width, 1 height (picture area), 2 filters, 3 colours, 4 flip,
 * 5 (unused), 6 ISO, 7 shutter, 8 aperture, 9 focal length, 10..13 camera WB multipliers, 14..17 daylight
 * multipliers, 18..26 XYZ → camera matrix (3×3, D65), 27 raw width, 28 raw height, 29 is_foveon.
 */
JNIEXPORT jdoubleArray JNICALL FN(nInfo)(JNIEnv *e, jclass c, jlong h) {
    libraw_data_t *d = H(h);
    double v[30]; memset(v, 0, sizeof v);
    v[0] = d->sizes.width; v[1] = d->sizes.height; v[2] = (double)d->idata.filters; v[3] = d->idata.colors;
    v[4] = d->sizes.flip; v[6] = d->other.iso_speed; v[7] = d->other.shutter;
    v[8] = d->other.aperture; v[9] = d->other.focal_len;
    for (int i = 0; i < 4; i++) { v[10 + i] = d->color.cam_mul[i]; v[14 + i] = d->color.pre_mul[i]; }
    for (int r = 0; r < 3; r++) for (int k = 0; k < 3; k++) v[18 + r * 3 + k] = d->color.cam_xyz[r][k];
    v[27] = d->sizes.raw_width; v[28] = d->sizes.raw_height; v[29] = d->idata.is_foveon;
    jdoubleArray a = (*e)->NewDoubleArray(e, 30);
    (*e)->SetDoubleArrayRegion(e, a, 0, 30, v);
    return a;
}

/** Colour of each pixel in an n×n block at the picture's top-left (0 R, 1 G, 2 B, 3 second G). */
JNIEXPORT jintArray JNICALL FN(nPattern)(JNIEnv *e, jclass c, jlong h, jint n) {
    libraw_data_t *d = H(h);
    jint *p = malloc(sizeof(jint) * n * n);
    for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) p[y * n + x] = libraw_COLOR(d, y, x);
    jintArray a = (*e)->NewIntArray(e, n * n);
    (*e)->SetIntArrayRegion(e, a, 0, n * n, p);
    free(p);
    return a;
}

/*
 * After nUnpack: the picture area's sensor values, black level removed and scaled so the white
 * level is [one]. One value per pixel for colour-filter sensors, three (RGB) for others.
 * Returns null when the data isn't available.
 */
JNIEXPORT jshortArray JNICALL FN(nRaw)(JNIEnv *e, jclass c, jlong h, jint one) {
    libraw_data_t *d = H(h);
    int w = d->sizes.width, ht = d->sizes.height, rw = d->sizes.raw_pitch / 2;
    int left = d->sizes.left_margin, top = d->sizes.top_margin;
    libraw_colordata_t *col = &d->rawdata.color;
    unsigned bl0 = col->black, *cb = col->cblack;
    int pr = cb[4], pc = cb[5];
    if (pr < 0 || pc < 0 || pr * pc > LIBRAW_CBLACK_SIZE - 6) { pr = 0; pc = 0; }
    double white = col->maximum;
    if (d->rawdata.raw_image) {
        if (w <= 0 || ht <= 0 || rw < w) return 0;
        uint16_t *raw = d->rawdata.raw_image;
        jshortArray a = (*e)->NewShortArray(e, (jsize)w * ht);
        if (!a) return 0;
        jshort *o = (*e)->GetPrimitiveArrayCritical(e, a, 0);
        for (int y = 0; y < ht; y++) {
            const uint16_t *row = raw + (size_t)(y + top) * rw + left;
            jshort *out = o + (size_t)y * w;
            for (int x = 0; x < w; x++) {
                int cc = libraw_COLOR(d, y, x) & 3;
                double b = bl0 + cb[cc] + (pr && pc ? cb[6 + ((y + top) % pr) * pc + ((x + left) % pc)] : 0);
                double s = (row[x] - b) / (white - b > 1 ? white - b : 1) * one; if (s > one) s = one;
                out[x] = (jshort)(s < 0 ? 0 : s > 65535 ? 65535 : (int)(s + 0.5));
            }
        }
        (*e)->ReleasePrimitiveArrayCritical(e, a, o, 0);
        return a;
    }
    uint16_t (*img4)[4] = d->rawdata.color4_image;
    uint16_t (*img3)[3] = d->rawdata.color3_image;
    if (!img4 && !img3) return 0;
    int pitch = d->sizes.raw_pitch / (img4 ? 8 : 6);
    jshortArray a = (*e)->NewShortArray(e, (jsize)w * ht * 3);
    if (!a) return 0;
    jshort *o = (*e)->GetPrimitiveArrayCritical(e, a, 0);
    for (int y = 0; y < ht; y++) for (int x = 0; x < w; x++) {
        size_t i = (size_t)(y + top) * pitch + x + left;
        for (int k = 0; k < 3; k++) {
            double v = img4 ? img4[i][k] : img3[i][k];
            double b = bl0 + cb[k];
            double s = (v - b) / (white - b > 1 ? white - b : 1) * one; if (s > one) s = one;
            o[((size_t)y * w + x) * 3 + k] = (jshort)(s < 0 ? 0 : s > 65535 ? 65535 : (int)(s + 0.5));
        }
    }
    (*e)->ReleasePrimitiveArrayCritical(e, a, o, 0);
    return a;
}

/** The camera's embedded preview: [0] = 1 JPEG / 2 RGB bitmap, then for RGB width, height (4 bytes each, big-endian) and the pixels. */
JNIEXPORT jbyteArray JNICALL FN(nThumb)(JNIEnv *e, jclass c, jlong h) {
    libraw_data_t *d = H(h);
    if (libraw_unpack_thumb(d) != LIBRAW_SUCCESS) return 0;
    libraw_thumbnail_t *t = &d->thumbnail;
    if (!t->thumb || t->tlength == 0) return 0;
    if (t->tformat == LIBRAW_THUMBNAIL_JPEG) {
        jbyteArray a = (*e)->NewByteArray(e, t->tlength + 1);
        jbyte k = 1;
        (*e)->SetByteArrayRegion(e, a, 0, 1, &k);
        (*e)->SetByteArrayRegion(e, a, 1, t->tlength, (jbyte *)t->thumb);
        return a;
    }
    if (t->tformat == LIBRAW_THUMBNAIL_BITMAP && t->tcolors == 3 && (unsigned)t->twidth * t->theight * 3 <= t->tlength) {
        unsigned n = (unsigned)t->twidth * t->theight * 3;
        jbyteArray a = (*e)->NewByteArray(e, n + 9);
        jbyte hd[9] = { 2, (jbyte)(t->twidth >> 24), (jbyte)(t->twidth >> 16), (jbyte)(t->twidth >> 8), (jbyte)t->twidth,
                        (jbyte)(t->theight >> 24), (jbyte)(t->theight >> 16), (jbyte)(t->theight >> 8), (jbyte)t->theight };
        (*e)->SetByteArrayRegion(e, a, 0, 9, hd);
        (*e)->SetByteArrayRegion(e, a, 9, n, (jbyte *)t->thumb);
        return a;
    }
    return 0;
}

JNIEXPORT jstring JNICALL FN(nVersion)(JNIEnv *e, jclass c) { return str(e, libraw_version()); }

/* ---- JPEG XL compressed DNGs (DNG 1.7, e.g. newer Samsung Galaxy and iPhone RAWs) ----
 * Decoded with libjxl (BSD-3-Clause), which the app already ships for its JPEG XL export; it's
 * looked up at run time, so this library doesn't depend on it. */
#include <dlfcn.h>
#include "jxl/decode.h"

static void *jx_lib;
static JxlDecoder *(*jx_create)(const JxlMemoryManager *);
static void (*jx_destroy)(JxlDecoder *);
static JxlDecoderStatus (*jx_subscribe)(JxlDecoder *, int);
static JxlDecoderStatus (*jx_input)(JxlDecoder *, const uint8_t *, size_t);
static void (*jx_close_input)(JxlDecoder *);
static JxlDecoderStatus (*jx_process)(JxlDecoder *);
static JxlDecoderStatus (*jx_info)(const JxlDecoder *, JxlBasicInfo *);
static JxlDecoderStatus (*jx_out_size)(const JxlDecoder *, const JxlPixelFormat *, size_t *);
static JxlDecoderStatus (*jx_out_buffer)(JxlDecoder *, const JxlPixelFormat *, void *, size_t);
static JxlDecoderStatus (*jx_keep_orient)(JxlDecoder *, JXL_BOOL);

JNIEXPORT jboolean JNICALL FN(nJxlInit)(JNIEnv *e, jclass c) {
    if (jx_process) return JNI_TRUE;
    const char *names[] = { "libjxl.so", "libjxl.so.0.11", "libjxl.so.0.10", "libjxl.so.0.9", "libjxl.so.0.8", "libjxl.so.0.7" };
    for (unsigned i = 0; i < sizeof names / sizeof *names && !jx_lib; i++) jx_lib = dlopen(names[i], RTLD_NOW);
    if (!jx_lib) return JNI_FALSE;
    jx_create = dlsym(jx_lib, "JxlDecoderCreate"); jx_destroy = dlsym(jx_lib, "JxlDecoderDestroy");
    jx_subscribe = dlsym(jx_lib, "JxlDecoderSubscribeEvents"); jx_input = dlsym(jx_lib, "JxlDecoderSetInput");
    jx_close_input = dlsym(jx_lib, "JxlDecoderCloseInput"); jx_info = dlsym(jx_lib, "JxlDecoderGetBasicInfo");
    jx_out_size = dlsym(jx_lib, "JxlDecoderImageOutBufferSize"); jx_out_buffer = dlsym(jx_lib, "JxlDecoderSetImageOutBuffer");
    jx_keep_orient = dlsym(jx_lib, "JxlDecoderSetKeepOrientation");
    JxlDecoderStatus (*proc)(JxlDecoder *) = dlsym(jx_lib, "JxlDecoderProcessInput");
    if (!jx_create || !jx_destroy || !jx_subscribe || !jx_input || !jx_info || !jx_out_size || !jx_out_buffer || !proc) return JNI_FALSE;
    jx_process = proc;
    return JNI_TRUE;
}

/** One JPEG XL tile → 16-bit samples ([channels] per pixel); its size goes to wh[0], wh[1]. */
JNIEXPORT jshortArray JNICALL FN(nJxl)(JNIEnv *e, jclass c, jbyteArray data, jint channels, jintArray wh) {
    if (!jx_process) return 0;
    jsize n = (*e)->GetArrayLength(e, data);
    jbyte *in = (*e)->GetByteArrayElements(e, data, 0);
    JxlDecoder *d = jx_create(0);
    jshortArray result = 0; uint16_t *buf = 0; JxlBasicInfo info;
    JxlPixelFormat fmt = { (uint32_t)channels, JXL_TYPE_UINT16, JXL_NATIVE_ENDIAN, 0 };
    if (!d) goto done;
    if (jx_keep_orient) jx_keep_orient(d, JXL_TRUE);
    if (jx_subscribe(d, JXL_DEC_BASIC_INFO | JXL_DEC_FULL_IMAGE) != JXL_DEC_SUCCESS) goto done;
    if (jx_input(d, (const uint8_t *)in, (size_t)n) != JXL_DEC_SUCCESS) goto done;
    if (jx_close_input) jx_close_input(d);
    for (;;) {
        JxlDecoderStatus s = jx_process(d);
        if (s == JXL_DEC_BASIC_INFO) {
            if (jx_info(d, &info) != JXL_DEC_SUCCESS) goto done;
        } else if (s == JXL_DEC_NEED_IMAGE_OUT_BUFFER) {
            size_t sz;
            if (jx_out_size(d, &fmt, &sz) != JXL_DEC_SUCCESS) goto done;
            buf = malloc(sz);
            if (!buf || jx_out_buffer(d, &fmt, buf, sz) != JXL_DEC_SUCCESS) goto done;
        } else if (s == JXL_DEC_FULL_IMAGE) {
            size_t cnt = (size_t)info.xsize * info.ysize * channels;
            result = (*e)->NewShortArray(e, (jsize)cnt);
            if (result) (*e)->SetShortArrayRegion(e, result, 0, (jsize)cnt, (jshort *)buf);
            jint s2[2] = { (jint)info.xsize, (jint)info.ysize };
            (*e)->SetIntArrayRegion(e, wh, 0, 2, s2);
            goto done;
        } else goto done; /* error, or input ended early */
    }
done:
    if (d) jx_destroy(d);
    free(buf);
    (*e)->ReleaseByteArrayElements(e, data, in, JNI_ABORT);
    return result;
}
