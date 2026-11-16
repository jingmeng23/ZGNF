# Local, transparent 4x line icons. No network assets or runtime dependencies.
Add-Type -AssemblyName System.Drawing
$iconDirectory = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../assets/account'))
[System.IO.Directory]::CreateDirectory($iconDirectory) | Out-Null

function Line($x1, $y1, $x2, $y2) { $graphics.DrawLine($pen, [single]$x1, [single]$y1, [single]$x2, [single]$y2) }
function Circle($x, $y, $size) { $graphics.DrawEllipse($pen, [single]$x, [single]$y, [single]$size, [single]$size) }
function Rect($x, $y, $width, $height) { $graphics.DrawRectangle($pen, [single]$x, [single]$y, [single]$width, [single]$height) }
function Outline($coordinates) {
  $points = for ($i = 0; $i -lt $coordinates.Length; $i += 2) { [System.Drawing.PointF]::new($coordinates[$i], $coordinates[$i + 1]) }
  $graphics.DrawLines($pen, [System.Drawing.PointF[]]$points)
}
function Icon($name, [scriptblock]$draw) {
  $bitmap = [System.Drawing.Bitmap]::new(96, 96)
  $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
  $pen = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml('#163F35'), 1.65)
  try {
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.ScaleTransform(4, 4)
    $pen.StartCap = $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    & $draw
    $bitmap.Save((Join-Path $iconDirectory ($name + '.png')), [System.Drawing.Imaging.ImageFormat]::Png)
  } finally { $pen.Dispose(); $graphics.Dispose(); $bitmap.Dispose() }
}
Icon 'user' { Circle 8 3 8; $graphics.DrawArc($pen, 4, 14, 16, 14, 185, 170) }
Icon 'wallet' { Rect 3 7 18 14; Outline @(3,7, 3,5, 17,3, 17,7); Rect 14 11 7 6; Line 17 14 18 14 }
Icon 'box' { Outline @(3,7, 12,3, 21,7, 21,18, 12,22, 3,18, 3,7, 12,11, 21,7); Line 12 11 12 22; Line 7.5 5 16.5 9 }
Icon 'truck' { Rect 2 5 12 12; Outline @(14,9, 18,9, 22,13, 22,17, 14,17); Line 16 10 16 13; Line 16 13 21 13; Circle 4 16 4; Circle 16 16 4 }
Icon 'aftersales' { $graphics.DrawArc($pen, 3, 3, 18, 18, 45, 290); Outline @(21,4, 21,9, 16,9); Line 8 12 16 12; Line 12 8 12 16 }
Icon 'heart' {
  $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
  try {
    $path.AddBezier(12,21, 9,18, 2,13, 2,8)
    $path.AddBezier(2,8, 2,2, 9,1, 12,7)
    $path.AddBezier(12,7, 15,1, 22,2, 22,8)
    $path.AddBezier(22,8, 22,13, 15,18, 12,21)
    $graphics.DrawPath($pen, $path)
  } finally { $path.Dispose() }
}
Icon 'clock' { Circle 3 3 18; Outline @(12,6, 12,12, 16,14) }
Icon 'ticket' { Outline @(3,5, 21,5, 21,9, 19,10, 19,14, 21,15, 21,19, 3,19, 3,15, 5,14, 5,10, 3,9, 3,5); Line 14 8 14 10; Line 14 14 14 16 }
Icon 'points' { Circle 2 2 20; Outline @(12,6, 13.8,9.6, 18,10.2, 15,13.2, 15.7,17.4, 12,15.4, 8.3,17.4, 9,13.2, 6,10.2, 10.2,9.6, 12,6) }
Icon 'pin' {
  $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
  try {
    $path.AddBezier(12,22, 8,17, 4,13, 4,9)
    $path.AddBezier(4,9, 4,-1, 20,-1, 20,9)
    $path.AddBezier(20,9, 20,13, 16,17, 12,22)
    $graphics.DrawPath($pen, $path); Circle 9 6 6
  } finally { $path.Dispose() }
}
Icon 'calendar' { Rect 3 5 18 16; Line 3 10 21 10; Line 8 2 8 7; Line 16 2 16 7; Outline @(8,15, 11,18, 17,13) }
Icon 'message' { Outline @(3,3, 21,3, 21,17, 10,17, 4,22, 4,17, 3,17, 3,3); Line 7 8 17 8; Line 7 12 14 12 }
Icon 'settings' {
  Circle 5 5 14; Circle 9 9 6
  for ($i = 0; $i -lt 8; $i++) {
    $angle = $i * [Math]::PI / 4
    Line (12 + 7 * [Math]::Cos($angle)) (12 + 7 * [Math]::Sin($angle)) (12 + 10 * [Math]::Cos($angle)) (12 + 10 * [Math]::Sin($angle))
  }
}
Write-Output ('Generated 13 local account icons in ' + $iconDirectory)
