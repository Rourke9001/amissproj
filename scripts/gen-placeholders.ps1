# Generates the placeholder background art for every screen into
# src\main\resources\amiss\resources\ (committed; bundled into the jar by Maven).
#
# These are deliberately plain, license-clean placeholders that give every screen a
# consistent look. To use real artwork instead, just overwrite the PNGs of the same
# name in src\main\resources\amiss\resources\ and rebuild -- no code change needed.
#
#   powershell -File scripts\gen-placeholders.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$proj = Split-Path $PSScriptRoot -Parent
$res  = Join-Path $proj 'src\main\resources\amiss\resources'
New-Item -ItemType Directory -Force (Join-Path $res 'screens') | Out-Null

# Shared palette (matches the existing Swing Color(135, 204, 250) light blue).
$top    = [System.Drawing.Color]::FromArgb(231, 243, 255)   # light tint (top)
$bottom = [System.Drawing.Color]::FromArgb(120, 178, 232)   # deeper blue (bottom)
$ink    = [System.Drawing.Color]::FromArgb(28, 58, 104)     # title text
$edge   = [System.Drawing.Color]::FromArgb(86, 128, 188)    # border

# name (relative to resources), width, height, title text, isBoard
$assets = @(
    @{ Name = 'screens\Login.png';          W = 400; H = 300; Title = 'Login';                  Board = $false }
    @{ Name = 'screens\MainGame.png';        W = 660; H = 380; Title = 'Jones in the Fast Lane'; Board = $false }
    @{ Name = 'board.png';                   W = 380; H = 340; Title = 'GAME BOARD';             Board = $true  }
    @{ Name = 'screens\Bank.png';            W = 290; H = 350; Title = 'Bank';                   Board = $false }
    @{ Name = 'screens\ApplianceStore.png';  W = 290; H = 350; Title = 'Socket City';            Board = $false }
    @{ Name = 'screens\ClothesStore.png';    W = 520; H = 300; Title = 'QT Clothing';            Board = $false }
    @{ Name = 'screens\FastFood.png';        W = 540; H = 320; Title = 'Monolith Burgers';       Board = $false }
    @{ Name = 'screens\Employment.png';      W = 800; H = 460; Title = 'Employment Office';      Board = $false }
    @{ Name = 'screens\Factory.png';         W = 290; H = 350; Title = 'Factory';                Board = $false }
    @{ Name = 'screens\Market.png';          W = 490; H = 280; Title = "Black's Market";         Board = $false }
    @{ Name = 'screens\Help.png';            W = 550; H = 300; Title = 'Help';                   Board = $false }
    @{ Name = 'screens\HighScore.png';       W = 480; H = 400; Title = 'High Scores';            Board = $false }
    @{ Name = 'screens\PawnShop.png';        W = 290; H = 350; Title = 'Pawn Shop';              Board = $false }
    @{ Name = 'screens\ZMart.png';           W = 290; H = 350; Title = 'Z-Mart';                 Board = $false }
    @{ Name = 'screens\LeSecurity.png';      W = 290; H = 350; Title = 'Le Security Apartments'; Board = $false }
    @{ Name = 'screens\RentOffice.png';      W = 320; H = 330; Title = 'Rent Office';            Board = $false }
    @{ Name = 'screens\Residence.png';       W = 300; H = 300; Title = 'Home';                   Board = $false }
    @{ Name = 'screens\University.png';       W = 500; H = 320; Title = 'Hi-Tech U';              Board = $false }
)

foreach ($a in $assets) {
    $w = [int]$a.W; $h = [int]$a.H
    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $g   = [System.Drawing.Graphics]::FromImage($bmp)
    try {
        $g.SmoothingMode     = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias

        # Vertical gradient fill. The rect is inflated 1px to dodge GDI+'s first-row artifact.
        $gradRect = New-Object System.Drawing.Rectangle(-1, -1, ($w + 2), ($h + 2))
        $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
            $gradRect, $top, $bottom, [System.Drawing.Drawing2D.LinearGradientMode]::Vertical)
        $g.FillRectangle($brush, 0, 0, $w, $h)
        $brush.Dispose()

        # Optional board grid.
        if ($a.Board) {
            $grid = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(70, 255, 255, 255), 1)
            $step = 40
            for ($x = $step; $x -lt $w; $x += $step) { $g.DrawLine($grid, $x, 0, $x, $h) }
            for ($y = $step; $y -lt $h; $y += $step) { $g.DrawLine($grid, 0, $y, $w, $y) }
            $grid.Dispose()
        }

        # 1px inner border.
        $pen = New-Object System.Drawing.Pen($edge, 1)
        $g.DrawRectangle($pen, 0, 0, ($w - 1), ($h - 1))
        $pen.Dispose()

        # Centered title.
        $sf = New-Object System.Drawing.StringFormat
        $sf.Alignment     = [System.Drawing.StringAlignment]::Center
        $sf.LineAlignment = [System.Drawing.StringAlignment]::Center
        $size = [int][Math]::Max(18, [Math]::Min(34, [Math]::Min($w, $h) / 8))
        $font = New-Object System.Drawing.Font('Segoe UI', $size, [System.Drawing.FontStyle]::Bold)
        $titleBrush = New-Object System.Drawing.SolidBrush($ink)
        $rectF = New-Object System.Drawing.RectangleF(0, 0, $w, $h)
        $g.DrawString($a.Title, $font, $titleBrush, $rectF, $sf)
        $font.Dispose(); $titleBrush.Dispose()

        # Small bottom caption marking these as placeholders.
        $capFont  = New-Object System.Drawing.Font('Segoe UI', 9)
        $capBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(150, 28, 58, 104))
        $capSf = New-Object System.Drawing.StringFormat
        $capSf.Alignment     = [System.Drawing.StringAlignment]::Center
        $capSf.LineAlignment = [System.Drawing.StringAlignment]::Far
        $g.DrawString('AmissProj - placeholder art', $capFont, $capBrush, $rectF, $capSf)
        $capFont.Dispose(); $capBrush.Dispose(); $capSf.Dispose(); $sf.Dispose()

        $path = Join-Path $res $a.Name
        $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
        Write-Host ("  {0,-28} {1}x{2}" -f $a.Name, $w, $h)
    }
    finally {
        $g.Dispose(); $bmp.Dispose()
    }
}

Write-Host ("Generated {0} placeholder image(s) -> {1}" -f $assets.Count, $res)
