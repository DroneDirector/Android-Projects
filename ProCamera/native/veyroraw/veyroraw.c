/*
 * VeyroCut Pro's small bridge to LibRaw (used for camera RAW formats other than DNG: Sony ARW,
 * Canon CR2/CR3, Nikon NEF, Fujifilm RAF, Olympus ORF, Panasonic RW2, Pentax PEF…).
 *
 * LibRaw itself is a separate shared library (libraw.so, LGPL 2.1) used only through its public C
 * API, so it can be replaced by the user. This file is VeyroCut Pro's own code.
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
    if (!d) { (*e)->ReleaseStringUTFChars(e, path, p); return 0; }
    int r = libraw_open_file(d, p);
    (*e)->ReleaseStringUTFChars(e, path, p);
    if (r != LIBRAW_SUCCESS) { libraw_close(d); return 0; }
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
