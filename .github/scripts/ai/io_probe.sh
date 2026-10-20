set -ex
pip install -q onnxruntime==1.19.2 numpy pillow
B=https://github.com/${GITHUB_REPOSITORY}/releases/download/ai-models-v1
mkdir -p m; cd m
for f in depth_anything_v2_small.onnx lama_fp32.onnx kokoro_v1_q8.onnx kokoro_af_heart.bin; do curl -fsSL -o $f $B/$f; done
curl -fsSL -o image.jpg https://huggingface.co/Carve/LaMa-ONNX/resolve/main/image.jpg
curl -fsSL -o mask.png https://huggingface.co/Carve/LaMa-ONNX/resolve/main/mask.png
curl -fsSL -o readme.md https://huggingface.co/Carve/LaMa-ONNX/raw/main/README.md; cp readme.md ../out/lama_readme.md
cd ..
cat > p.py <<'PY'
import onnxruntime as ort, numpy as np, time
from PIL import Image
def io(path):
    s = ort.InferenceSession(path, providers=['CPUExecutionProvider'])
    print(path, 'IN', [(i.name, i.shape, i.type) for i in s.get_inputs()], 'OUT', [(o.name, o.shape, o.type) for o in s.get_outputs()])
    return s
d = io('m/depth_anything_v2_small.onnx')
img = Image.open('m/image.jpg').convert('RGB')
x = np.asarray(img.resize((518, 294)), np.float32) / 255.0
x = (x - [0.485,0.456,0.406]) / [0.229,0.224,0.225]
x = x.transpose(2,0,1)[None].astype(np.float32)
t=time.time(); o = d.run(None, {d.get_inputs()[0].name: x})[0]; print('depth out', o.shape, o.min(), o.max(), 'time', time.time()-t)
Image.fromarray((255*(o[0]-o.min())/(o.max()-o.min())).astype(np.uint8)).save('out/depth.png')
l = io('m/lama_fp32.onnx')
im = np.asarray(img.resize((512,512)), np.float32)/255.0
mk = (np.asarray(Image.open('m/mask.png').convert('L').resize((512,512)), np.float32) > 127).astype(np.float32)
t=time.time(); o = l.run(None, {l.get_inputs()[0].name: im.transpose(2,0,1)[None], l.get_inputs()[1].name: mk[None,None]})[0]
print('lama out', o.shape, o.min(), o.max(), 'time', time.time()-t)
oo = o[0].transpose(1,2,0); oo = oo if oo.max() > 2 else oo*255
Image.fromarray(np.clip(oo,0,255).astype(np.uint8)).save('out/lama.png')
k = io('m/kokoro_v1_q8.onnx')
v = np.fromfile('m/kokoro_af_heart.bin', dtype=np.float32); print('voice', v.shape, v.reshape(-1,256).shape)
PY
python3 p.py 2>&1 | tee out/io.txt
