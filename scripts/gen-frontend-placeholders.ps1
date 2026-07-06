# Regenerates the SPA's placeholder storefront images by cropping each stop's
# tile out of the board template (docs/design/boardv2.png, a uniform 5x4 grid).
#
# The React app loads storefronts from frontend/public/assets/locations/<id>.png
# via src/assets/manifest.ts. When the final 16:9 art arrives, replace the PNGs
# in that folder (same names) - no code change, no rebuild of imports needed.
#
#   powershell -File scripts\gen-frontend-placeholders.ps1
#
# The stop ids and their (row, col) cells mirror amiss-core's Board.CELLS /
# GET /api/board; each output is the cell's central 16:9 band at 640x360.
param(
    [string]$BoardPng = "$PSScriptRoot\..\docs\design\boardv2.png",
    [string]$OutDir = "$PSScriptRoot\..\frontend\public\assets\locations",
    [double]$TopOffsetFrac = 0.18
)
Add-Type -AssemblyName System.Drawing
$img = [System.Drawing.Bitmap]::FromFile((Resolve-Path $BoardPng))
$cellW = $img.Width / 5.0
$cellH = $img.Height / 4.0
$cropH = $cellW * 9.0 / 16.0

$stops = @(
    @{ id = 'LOW_COST_HOUSING';       row = 0; col = 2 },
    @{ id = 'PAWN_SHOP';              row = 0; col = 3 },
    @{ id = 'Z_MART';                 row = 0; col = 4 },
    @{ id = 'MONOLITH_BURGERS';       row = 1; col = 4 },
    @{ id = 'QT_CLOTHING';            row = 2; col = 4 },
    @{ id = 'SOCKET_CITY';            row = 3; col = 4 },
    @{ id = 'HI_TECH_U';              row = 3; col = 3 },
    @{ id = 'EMPLOYMENT_OFFICE';      row = 3; col = 1 },
    @{ id = 'FACTORY';                row = 3; col = 0 },
    @{ id = 'BANK';                   row = 2; col = 0 },
    @{ id = 'BLACKS_MARKET';          row = 1; col = 0 },
    @{ id = 'LE_SECURITY_APARTMENTS'; row = 0; col = 0 },
    @{ id = 'RENT_OFFICE';            row = 0; col = 1 }
)

New-Item -ItemType Directory -Force $OutDir | Out-Null
foreach ($s in $stops) {
    $x = [int]($s.col * $cellW)
    $y = [int]($s.row * $cellH + $TopOffsetFrac * $cellH)
    if ($y + $cropH -gt ($s.row + 1) * $cellH) { $y = [int](($s.row + 1) * $cellH - $cropH) }
    $src = New-Object System.Drawing.Rectangle($x, $y, [int]$cellW, [int]$cropH)
    $out = New-Object System.Drawing.Bitmap(640, 360)
    $g = [System.Drawing.Graphics]::FromImage($out)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.DrawImage($img, (New-Object System.Drawing.Rectangle(0, 0, 640, 360)), $src, [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    $name = $s.id.ToLower().Replace('_', '-')
    $out.Save((Join-Path $OutDir "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()
    Write-Output "wrote $name.png"
}
$img.Dispose()
