# 生成网页端图标 PNG（192 / 512）：
#   powershell -ExecutionPolicy Bypass -File tools/make-web-icons.ps1 <输出目录>
# 设计与 assets/web/icon.svg 保持一致：蓝色渐变圆角方块 + 三条“行” + A 字符。
param(
    [string]$OutDir = "app/src/main/assets/web"
)

Add-Type -AssemblyName System.Drawing

function New-LineTransIcon {
    param([int]$Size, [string]$OutFile)

    $bmp = New-Object System.Drawing.Bitmap($Size, $Size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    $rect = New-Object System.Drawing.Rectangle(0, 0, $Size, $Size)
    $c1 = [System.Drawing.Color]::FromArgb(255, 77, 107, 254)
    $c2 = [System.Drawing.Color]::FromArgb(255, 122, 162, 255)
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $c1, $c2, 45.0)

    $radius = [int]($Size * 0.226)
    $d = $radius * 2
    $shape = New-Object System.Drawing.Drawing2D.GraphicsPath
    $shape.AddArc(0, 0, $d, $d, 180, 90)
    $shape.AddArc($Size - $d, 0, $d, $d, 270, 90)
    $shape.AddArc($Size - $d, $Size - $d, $d, $d, 0, 90)
    $shape.AddArc(0, $Size - $d, $d, $d, 90, 90)
    $shape.CloseFigure()
    $g.FillPath($brush, $shape)

    # 三条“行”
    $white = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    $soft = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(190, 255, 255, 255))
    $x = [int]($Size * 0.19)
    $w = [int]($Size * 0.62)
    $h = [int]($Size * 0.052)
    $g.FillRectangle($white, $x, [int]($Size * 0.24), [int]($w * 0.72), $h)
    $g.FillRectangle($soft, $x, [int]($Size * 0.37), $w, $h)
    $g.FillRectangle($soft, $x, [int]($Size * 0.50), [int]($w * 0.53), $h)

    # A / 文 对照符号（用一个粗体 A 近似）
    $fontSize = [int]($Size * 0.30)
    $font = New-Object System.Drawing.Font("Segoe UI", $fontSize, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = [System.Drawing.StringAlignment]::Center
    $fmt.LineAlignment = [System.Drawing.StringAlignment]::Center
    $rectF = New-Object System.Drawing.RectangleF(0, 0, $Size, $Size)
    $g.DrawString("A", $font, $white, $rectF, $fmt)

    $g.Dispose()
    $bmp.Save($OutFile, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "生成 $OutFile"
}

New-LineTransIcon -Size 192 -OutFile (Join-Path $OutDir "icon-192.png")
New-LineTransIcon -Size 512 -OutFile (Join-Path $OutDir "icon-512.png")
