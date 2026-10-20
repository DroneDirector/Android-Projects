---
license: apache-2.0
language:
- en
pipeline_tag: image-to-image
tags:
- art
- lama
- inpainting
---
# LaMa Inpainting Model

This ONNX model is a port of the original PyTorch big-lama model.

> HG Space: https://huggingface.co/spaces/Carve/LaMa-Demo-ONNX
## Description

There are two versions of the model:

### 1. `lama_fp32.onnx` (RECOMMENDED)

This version was exported using the old torch to ONNX converter (`torch.onnx.export`).

**Notes:**
1. **Custom FourierUnitJIT**: A custom [FourierUnitJIT](https://github.com/Carve-Photos/lama/blob/main/saicinpainting/training/modules/ffc.py) implementation is used since the original cannot be directly ported to ONNX without overhead. The result is identical to the original model.
2. **Fixed Input Shape**: The input shape is fixed at 512x512 pixels. Although dynamic input shapes are possible, they would require resolving issues with dynamic padding in the `irfft` and `rfftn` functions in `ffc.py`.
3. **Opset Version 17**: This model uses opset version 17.
4. **Exportable to TensorRT**: The model can be successfully used in TensorRT, etc.
> if you need other resolution - export it using our [jupyter notebook](https://colab.research.google.com/github/Carve-Photos/lama/blob/main/export_LaMa_to_onnx.ipynb)

### 2. `lama.onnx` (NOT RECOMMENDED)

This version was exported using the new torch to ONNX converter (`torch.onnx.dynamo_export`).

**Notes:**
1. **Custom DFT irfftn Logic**: Uses a custom irfftn ONNX logic (patched `onnxscript`).
2. **Fixed Input Shape**: The input shape is fixed at 512x512 pixels.
3. **Opset Version 18**: This model uses opset version 18.
4. **Performance**: The model works slowly due to issues with `torch.onnx.dynamo_export` and optimization of the ONNX model.

## Resources

- Original repository: [advimman/lama](https://github.com/advimman/lama)
- Repository with custom implementation of exportable LaMa: [Carve-Photos/lama](https://github.com/Carve-Photos/lama)

## Example

**Original image:**
![original image](./image.jpg)


**lama_fp32.onnx - output:**
![onnx output](./output_onnx_fp32.png)

**lama.onnx - output:**
![onnx output](./output_onnx.png)

**Original model output:**
![original model output](./output_orig.png)