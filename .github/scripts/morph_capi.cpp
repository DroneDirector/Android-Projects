#include "morph.h"
#include <cstring>
using namespace morph;
extern "C" {
void flow_rgba(const uint8_t* a, const uint8_t* b, int w, int h, float* out) {
    Flow f = computeFlow(a, b, w, h);
    for (size_t i = 0; i < (size_t)w * h; i++) { out[i * 2] = f.u[i]; out[i * 2 + 1] = f.v[i]; }
}
void morph_rgba(const uint8_t* a, const uint8_t* b, int w, int h, float t, uint8_t* out) {
    Img la = lumaFromRgba(a, w, h), lb = lumaFromRgba(b, w, h);
    Flow ab = computeFlow(a, b, w, h), ba = computeFlow(b, a, w, h);
    Img oa = occlusion(ab, ba), ob = occlusion(ba, ab);
    Flow mt = projectFlow(ab, ba, la, lb, oa, ob, t);
    render(a, b, w, h, mt, oa, ob, t, out);
}
}
extern "C" void morph_flows(const uint8_t* a, const uint8_t* b, int w, int h, float t, const float* fab, const float* fba, uint8_t* out, float* occOut) {
    Img la = lumaFromRgba(a, w, h), lb = lumaFromRgba(b, w, h);
    Flow ab(w, h), ba(w, h);
    for (size_t i = 0; i < (size_t)w * h; i++) { ab.u[i] = fab[i*2]; ab.v[i] = fab[i*2+1]; ba.u[i] = fba[i*2]; ba.v[i] = fba[i*2+1]; }
    Img oa = occlusion(ab, ba), ob = occlusion(ba, ab);
    Flow mt = projectFlow(ab, ba, la, lb, oa, ob, t);
    render(a, b, w, h, mt, oa, ob, t, out);
    for (size_t i = 0; i < (size_t)w * h; i++) { occOut[i*4] = oa.d[i]; occOut[i*4+1] = ob.d[i]; occOut[i*4+2] = mt.u[i]; occOut[i*4+3] = mt.v[i]; }
}
extern "C" void flow_only(const uint8_t* a, const uint8_t* b, int w, int h, float* out) { flow_rgba(a, b, w, h, out); }
