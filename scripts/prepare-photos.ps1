# Resize everything in photos\ to 1920x1080 (cover crop, EXIF rotation applied) as JPEGs in
# app\src\main\assets\photos\. Run it, then .\scripts\deploy.ps1 to ship them.
param([int] $Width = 1920, [int] $Height = 1080, [int] $Quality = 85)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path $PSScriptRoot -Parent
$source = Join-Path $root 'photos'
$dest = Join-Path $root 'app\src\main\assets\photos'
New-Item -ItemType Directory -Force $dest | Out-Null
Get-ChildItem $dest -File | Remove-Item

$jpeg = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object MimeType -eq 'image/jpeg'
$params = New-Object System.Drawing.Imaging.EncoderParameters 1
$params.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality), ([long] $Quality)

# EXIF orientation tag -> rotate/flip needed to display upright.
$rotations = @{
    2 = 'RotateNoneFlipX'; 3 = 'Rotate180FlipNone'; 4 = 'Rotate180FlipX'
    5 = 'Rotate90FlipX';   6 = 'Rotate90FlipNone';  7 = 'Rotate270FlipX'; 8 = 'Rotate270FlipNone'
}

$files = Get-ChildItem $source -File | Where-Object { $_.Extension -match '^\.(jpe?g|png|bmp|gif|tiff?)$' }
if (-not $files) { throw "No images in $source (HEIC isn't supported by Windows' built-in decoder; export as JPEG first)." }

$i = 0
foreach ($f in $files) {
    $img = [System.Drawing.Image]::FromFile($f.FullName)
    try {
        if ($img.PropertyIdList -contains 0x0112) {
            $o = [int] $img.GetPropertyItem(0x0112).Value[0]
            if ($rotations.ContainsKey($o)) { $img.RotateFlip([System.Drawing.RotateFlipType]::($rotations[$o])) }
        }
        # Scale to cover the target, then centre-crop.
        $scale = [Math]::Max($Width / $img.Width, $Height / $img.Height)
        $w = [int] [Math]::Ceiling($img.Width * $scale)
        $h = [int] [Math]::Ceiling($img.Height * $scale)
        $bmp = New-Object System.Drawing.Bitmap $Width, $Height
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.DrawImage($img, [int](($Width - $w) / 2), [int](($Height - $h) / 2), $w, $h)
        $g.Dispose()
        $i++
        $out = Join-Path $dest ('photo_{0:D3}.jpg' -f $i)
        $bmp.Save($out, $jpeg, $params)
        $bmp.Dispose()
        Write-Host "$($f.Name) -> $(Split-Path $out -Leaf)"
    } finally { $img.Dispose() }
}
Write-Host "$i photos ready in $dest"
