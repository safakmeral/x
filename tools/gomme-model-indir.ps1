# G-43: downloads the task-memory embedding model files (multilingual-e5-small, MIT) from the
# official Hugging Face repository at a pinned revision into models\gomme\ (git-ignored) and checks
# SHA-256. Needed for the tokenizer reference test (:core), the desktop tool and tools\gomme-olcum.ps1.
#
#   .\tools\gomme-model-indir.ps1            (model + tokenizer, ~123 MB)
#   .\tools\gomme-model-indir.ps1 -Referans  (also tokenizer.json, 17 MB, for tools\gomme-referans.py)
param([switch] $Referans)

$ErrorActionPreference = "Stop"
$kok = Split-Path -Parent $PSScriptRoot
$hedef = Join-Path $kok "models\gomme"
$surum = "614241f622f53c4eeff9890bdc4f31cfecc418b3"
$taban = "https://huggingface.co/intfloat/multilingual-e5-small/resolve/$surum/onnx"

# Same values as app/src/main/java/tr/yasliasistan/app/embedding/EmbeddingModelFiles.kt
$dosyalar = @(
    @{ Ad = "model_qint8_avx512_vnni.onnx"; Sha = "dd476dd0c2514e9b9be83aeb3853fac0763e0bdf4a71645407587d77c48a2d88" },
    @{ Ad = "sentencepiece.bpe.model"; Sha = "cfc8146abe2a0488e9e2a0c56de7952f7c11ab059eca145a0a727afce0db2865" }
)
if ($Referans) {
    $dosyalar += @{ Ad = "tokenizer.json"; Sha = "0b44a9d7b51c3c62626640cda0e2c2f70fdacdc25bbbd68038369d14ebdf4c39" }
}

New-Item -ItemType Directory -Force $hedef | Out-Null
foreach ($d in $dosyalar) {
    $yol = Join-Path $hedef $d.Ad
    if ((Test-Path $yol) -and ((Get-FileHash $yol -Algorithm SHA256).Hash.ToLower() -eq $d.Sha)) {
        Write-Host "Zaten var: $($d.Ad)"
        continue
    }
    Write-Host "İndiriliyor: $($d.Ad)"
    & curl.exe -sSfL -o $yol "$taban/$($d.Ad)"
    if ($LASTEXITCODE -ne 0) { throw "İndirme başarısız: $($d.Ad)" }
    $sha = (Get-FileHash $yol -Algorithm SHA256).Hash.ToLower()
    if ($sha -ne $d.Sha) {
        Remove-Item $yol -Force
        throw "SHA-256 tutmadı, dosya silindi: $($d.Ad)"
    }
}
Write-Host "Model dosyaları hazır: $hedef"
