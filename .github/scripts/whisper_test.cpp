// Host test of the app's whisper.cpp settings (same params as whisper_jni.cpp).
#include <cstdio>
#include <vector>
#include <string>
#include "whisper.h"
int main(int argc, char **argv) {
    FILE *f = fopen(argv[2], "rb"); std::vector<float> pcm; float v;
    while (fread(&v, 4, 1, f) == 1) pcm.push_back(v); fclose(f);
    whisper_context_params cp = whisper_context_default_params(); cp.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(argv[1], cp);
    if (!ctx) { printf("INIT FAILED\n"); return 1; }
    whisper_full_params wp = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wp.n_threads = 4; wp.print_progress = false; wp.print_realtime = false; wp.print_special = false; wp.print_timestamps = false;
    wp.no_context = true; wp.token_timestamps = true; wp.max_len = 1; wp.split_on_word = true; wp.suppress_blank = true; wp.language = "auto";
    if (whisper_full(ctx, wp, pcm.data(), (int) pcm.size()) != 0) { printf("FULL FAILED\n"); return 1; }
    for (int i = 0; i < whisper_full_n_segments(ctx); i++)
        printf("%ld\t%ld\t%s\n", (long) whisper_full_get_segment_t0(ctx, i) * 10, (long) whisper_full_get_segment_t1(ctx, i) * 10, whisper_full_get_segment_text(ctx, i));
    whisper_free(ctx);
    return 0;
}
