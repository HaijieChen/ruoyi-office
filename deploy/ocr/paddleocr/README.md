# PaddleOCR on 10.20.32.1

Listen 8099. OA yaml: yudao.finance.invoice-ocr.base-url: http://10.20.32.1:8099

Start a CPU PaddleOCR HTTP wrapper on this host (docker or venv). POST /ocr/invoice {"fileUrl":"..."} -> {feeDate, amount, rawText}.
