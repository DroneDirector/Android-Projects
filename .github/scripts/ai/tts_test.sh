set -ex
pip install -q onnxruntime==1.19.2 numpy soundfile faster-whisper
B=https://github.com/${GITHUB_REPOSITORY}/releases/download/ai-models-v1
mkdir -p m; for f in kokoro_v1_q8.onnx kokoro_af_heart.bin kokoro_bm_george.bin misaki_us_gold.json misaki_us_silver.json misaki_gb_gold.json misaki_gb_silver.json kokoro_tokenizer.json; do curl -fsSL -o m/$f $B/$f; done
curl -fsSL -o kc.zip https://github.com/JetBrains/kotlin/releases/download/v2.0.21/kotlin-compiler-2.0.21.zip && unzip -q kc.zip
curl -fsSL -o json.jar https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar
python3 - <<'PY'
s = open('ProCamera/app/src/main/java/com/dronedirector/veyrocutpro/editor/Voiceover.kt').read()
g = s[s.index('class G2P private constructor'):]
g = g.replace('class G2P private constructor', 'class G2P constructor')
a = g.index('        private var us: G2P? = null'); b = g.index('        private fun load(f: File)')
g = g[:a] + g[b:].replace('private fun load(f: File)', 'fun load(f: File)', 1)
main = '''
fun main() {
    val us = G2P(G2P.load(File("m/misaki_us_gold.json")), G2P.load(File("m/misaki_us_silver.json")), false)
    val gb = G2P(G2P.load(File("m/misaki_gb_gold.json")), G2P.load(File("m/misaki_gb_silver.json")), true)
    val lines = File("sent.txt").readLines()
    File("ph_us.txt").writeText(lines.joinToString("\\n") { us.phonemize(it) })
    File("ph_gb.txt").writeText(lines.joinToString("\\n") { gb.phonemize(it) })
}
'''
open('G2PTest.kt', 'w').write('import org.json.JSONObject\nimport java.io.File\n' + g + main)
PY
cat > sent.txt <<'TXT'
Welcome to AlcorCut Pro, the video editor that fits in your pocket.
On the 3rd of July 2026, we filmed 150 drone shots over the Faroe Islands.
It costs $4.99 and saves you 25% of your time.
Dr. Smith's interview starts at 9:30, so don't be late!
The quick brown fox jumps over the lazy dog.
Glimmerfrost and Thundervale are made-up words that aren't in any dictionary.
TXT
kotlinc/bin/kotlinc G2PTest.kt -cp json.jar -include-runtime -d g2p.jar 2>&1 | grep -v warning || true
java -cp g2p.jar:json.jar G2PTestKt
cp sent.txt ph_us.txt ph_gb.txt out/
cat > k.py <<'PY'
import onnxruntime as ort, numpy as np, soundfile as sf, json
from faster_whisper import WhisperModel
vocab = json.load(open('m/kokoro_tokenizer.json'))['model']['vocab']
s = ort.InferenceSession('m/kokoro_v1_q8.onnx', providers=['CPUExecutionProvider'])
wm = WhisperModel('base.en', device='cpu', compute_type='int8')
sent = open('sent.txt').read().splitlines()
for acc, voice in (('us', 'af_heart'), ('gb', 'bm_george')):
    v = np.fromfile(f'm/kokoro_{voice}.bin', dtype=np.float32).reshape(-1, 256)
    for i, ph in enumerate(open(f'ph_{acc}.txt').read().splitlines()):
        toks = [vocab[c] for c in ph if c in vocab]
        miss = [c for c in ph if c not in vocab]
        ids = np.array([[0] + toks + [0]], dtype=np.int64)
        st = v[min(len(toks), len(v) - 1)][None]
        wav = s.run(None, {'input_ids': ids, 'style': st, 'speed': np.array([1.0], np.float32)})[0][0]
        sf.write(f'out/{acc}_{i}.wav', wav, 24000)
        segs, _ = wm.transcribe(f'out/{acc}_{i}.wav', language='en')
        text = ' '.join(x.text.strip() for x in segs)
        print(f'[{acc} {voice}] {sent[i]}\n   ph: {ph}\n   heard: {text}\n   missing: {miss}')
PY
python3 k.py 2>&1 | tee out/tts.txt
