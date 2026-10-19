"""Exports MobileSAM (Apache 2.0, github.com/ChaoningZhang/MobileSAM) to ONNX for VeyroCut Pro's Magic Mask,
then checks the exported models against PyTorch on test pictures (fp32 and int8 encoders)."""
import sys, os, numpy as np, torch, cv2
sys.path.insert(0, 'MobileSAM')
from mobile_sam import sam_model_registry
from mobile_sam.utils.onnx import SamOnnxModel
import onnxruntime as ort
from onnxruntime.quantization import quantize_dynamic, QuantType
import skimage.data as sd

os.makedirs('models', exist_ok=True); os.makedirs('out', exist_ok=True)
sam = sam_model_registry['vit_t'](checkpoint='MobileSAM/weights/mobile_sam.pt').eval()
MEAN = [123.675, 116.28, 103.53]; STD = [58.395, 57.12, 57.375]

class Enc(torch.nn.Module):
    """Input: 1×3×1024×1024 RGB 0..255 (longest side resized to 1024, padded bottom/right with the mean colour)."""
    def __init__(s):
        super().__init__(); s.e = sam.image_encoder
        s.register_buffer('m', torch.tensor(MEAN).view(1, 3, 1, 1)); s.register_buffer('d', torch.tensor(STD).view(1, 3, 1, 1))
    def forward(s, x): return s.e((x - s.m) / s.d)

class Dec(torch.nn.Module):
    """Prompt points (1024-space, label 1 = object, 0 = background, 2/3 = box corners, -1 = padding) → 256×256 mask logits + score."""
    def __init__(s):
        super().__init__(); s.o = SamOnnxModel(sam, return_single_mask=True)
    def forward(s, emb, coords, labels, mask_in, has_mask):
        sp = s.o._embed_points(coords, labels)
        de = s.o._embed_masks(mask_in, has_mask)
        masks, scores = s.o.model.mask_decoder.predict_masks(image_embeddings=emb, image_pe=s.o.model.prompt_encoder.get_dense_pe(), sparse_prompt_embeddings=sp, dense_prompt_embeddings=de)
        # All four masks: [0] = the single-object answer (best with several points or a box),
        # [1..3] = small / medium / large answers for one point (whole, part, sub-part).
        return masks, scores

x = torch.zeros(1, 3, 1024, 1024)
torch.onnx.export(Enc(), x, 'models/sam_encoder.onnx', input_names=['image'], output_names=['embeddings'], opset_version=17, do_constant_folding=True)
emb = torch.zeros(1, 256, 64, 64)
torch.onnx.export(Dec(), (emb, torch.zeros(1, 5, 2), torch.zeros(1, 5), torch.zeros(1, 1, 256, 256), torch.zeros(1)), 'models/sam_decoder.onnx',
                  input_names=['embeddings', 'coords', 'labels', 'mask_input', 'has_mask'], output_names=['mask', 'score'],
                  dynamic_axes={'coords': {1: 'n'}, 'labels': {1: 'n'}}, opset_version=17, do_constant_folding=True)
quantize_dynamic('models/sam_encoder.onnx', 'models/sam_encoder_int8.onnx', weight_type=QuantType.QUInt8)
for f in sorted(os.listdir('models')): print(f, os.path.getsize('models/' + f) // 1024, 'KB')

def prep(img):
    h, w = img.shape[:2]; k = 1024 / max(h, w)
    r = cv2.resize(img, (int(round(w * k)), int(round(h * k))), interpolation=cv2.INTER_AREA).astype(np.float32)
    canvas = np.empty((1024, 1024, 3), np.float32); canvas[:] = MEAN
    canvas[:r.shape[0], :r.shape[1]] = r
    return canvas.transpose(2, 0, 1)[None], k

dec = ort.InferenceSession('models/sam_decoder.onnx')
tests = [('astronaut', sd.astronaut(), [[230, 95, 1]]), ('astronaut_box', sd.astronaut(), [[140, 20, 2], [330, 512, 3]]),
         ('coffee', sd.coffee(), [[300, 160, 1], [520, 360, 0]]), ('chelsea', sd.chelsea(), [[200, 140, 1]])]
for encf in ['sam_encoder.onnx', 'sam_encoder_int8.onnx']:
    enc = ort.InferenceSession('models/' + encf)
    for name, img, pts in tests:
        inp, k = prep(img)
        e = enc.run(None, {'image': inp})[0]
        coords = np.array([[[p[0] * k, p[1] * k] for p in pts]], np.float32)
        labels = np.array([[p[2] for p in pts]], np.float32)
        if all(p[2] in (1, 0) for p in pts):  # points only: add the padding point SAM expects
            coords = np.concatenate([coords, np.zeros((1, 1, 2), np.float32)], 1); labels = np.concatenate([labels, -np.ones((1, 1), np.float32)], 1)
        m4, s4 = dec.run(None, {'embeddings': e, 'coords': coords, 'labels': labels, 'mask_input': np.zeros((1, 1, 256, 256), np.float32), 'has_mask': np.zeros(1, np.float32)})
        # SAM's own choice: the single answer for several prompts, else the best of the three.
        pick = 0 if coords.shape[1] > 2 or labels[0, 0] == 2 else 1 + int(np.argmax(s4[0, 1:]))
        m = m4[:, pick:pick + 1]; s = s4[:, pick:pick + 1]
        h, w = img.shape[:2]
        low = cv2.resize(m[0, 0], (1024, 1024))[:int(round(h * k)), :int(round(w * k))]
        mask = cv2.resize(low, (w, h)) > 0
        np.save(f'out/{name}_{encf[:-5]}.npy', mask)
        over = img.copy(); over[mask] = (over[mask] * 0.45 + np.array([255, 179, 0]) * 0.55).astype(np.uint8)
        for p in pts: cv2.circle(over, (p[0], p[1]), 6, (0, 255, 0) if p[2] != 0 else (255, 0, 0), -1)
        cv2.imwrite(f'out/{name}_{encf[:-5]}.jpg', cv2.cvtColor(over, cv2.COLOR_RGB2BGR))
        print(encf, name, 'score', float(s.ravel()[0]), 'area', mask.mean())
for name, *_ in tests:
    a = np.load(f'out/{name}_sam_encoder.npy'); b = np.load(f'out/{name}_sam_encoder_int8.npy')
    print('IoU fp32 vs int8', name, (a & b).sum() / max(1, (a | b).sum()))
