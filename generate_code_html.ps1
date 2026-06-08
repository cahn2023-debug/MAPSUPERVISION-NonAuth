$outputFile = "docs/PROJECT_SOURCE_CODE.html"
$title = "MapSupervision - Toàn bộ Code Dự Án"

$htmlHeader = @"
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$title</title>
    <!-- Highlight.js for Syntax Highlighting -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/atom-one-dark.min.css">
    <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/kotlin.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/xml.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/gradle.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/markdown.min.js"></script>
    <script>hljs.highlightAll();</script>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; display: flex; margin: 0; height: 100vh; overflow: hidden; background: #1e1e1e; color: #d4d4d4; }
        #sidebar { width: 350px; background: #252526; border-right: 1px solid #333; padding: 15px; overflow-y: auto; display: flex; flex-direction: column; }
        #content { flex: 1; padding: 20px; overflow-y: auto; background: #1e1e1e; scroll-behavior: smooth; }
        h1, h2, h3 { color: #fff; margin-top: 0; }
        .search-container { margin-bottom: 15px; flex-shrink: 0; }
        #search-box { width: 100%; padding: 10px; box-sizing: border-box; background: #3c3c3c; border: 1px solid #555; color: #fff; border-radius: 4px; font-size: 14px; }
        #search-box:focus { outline: none; border-color: #007acc; }
        #file-list { overflow-y: auto; flex: 1; }
        a.file-link { color: #4fc1ff; text-decoration: none; display: block; padding: 6px 8px; font-size: 13px; word-wrap: break-word; border-radius: 4px; margin-bottom: 2px; }
        a.file-link:hover { text-decoration: none; background: #2a2d2e; color: #fff; }
        .file-section { margin-bottom: 40px; border: 1px solid #333; border-radius: 8px; background: #2d2d2d; padding: 15px; box-shadow: 0 4px 6px rgba(0,0,0,0.3); }
        .file-header { font-weight: bold; font-size: 16px; margin-bottom: 10px; border-bottom: 1px solid #444; padding-bottom: 8px; color: #4ec9b0; display: flex; justify-content: space-between; align-items: center; }
        .back-to-top { font-size: 12px; color: #007acc; text-decoration: none; font-weight: normal; padding: 4px 8px; background: #1e1e1e; border-radius: 4px; }
        .back-to-top:hover { background: #333; color: #fff; }
        pre { margin: 0; border-radius: 4px; overflow-x: auto; }
        code { font-family: "Consolas", "Courier New", monospace; font-size: 13px; line-height: 1.5; }
        ::-webkit-scrollbar { width: 10px; height: 10px; }
        ::-webkit-scrollbar-track { background: #1e1e1e; }
        ::-webkit-scrollbar-thumb { background: #424242; border-radius: 5px; }
        ::-webkit-scrollbar-thumb:hover { background: #4f4f4f; }
    </style>
</head>
<body>
    <div id="sidebar">
        <h2>Mục lục dự án</h2>
        <div class="search-container">
            <input type="text" id="search-box" placeholder="Tìm kiếm file (VD: MainActivity)..." onkeyup="filterFiles()">
        </div>
        <div id="file-list">
"@

Out-File -FilePath $outputFile -InputObject $htmlHeader -Encoding utf8

# Get all source files, excluding build, .git, etc.
$files = Get-ChildItem -Include *.kt, *.xml, *.gradle.kts, *.md, *.pro, *.properties, *.json -Recurse | Where-Object { 
    $_.FullName -notmatch "\\build\\" -and 
    $_.FullName -notmatch "\\.gradle\\" -and 
    $_.FullName -notmatch "\\.idea\\" -and 
    $_.FullName -notmatch "\\.git\\" -and 
    $_.FullName -notmatch "\\.codegraph\\" -and 
    $_.FullName -notmatch "\\.kiro\\" 
}

Write-Host "Found $($files.Count) files. Generating Sidebar..."

# Write Sidebar Links
foreach ($file in $files) {
    $relativePath = $file.FullName.Replace((Get-Location).Path + "\", "").Replace("\", "/")
    $id = $relativePath -replace '[^a-zA-Z0-9]', '_'
    $nameLower = $relativePath.ToLower()
    $link = "<a class='file-link' href='#$id' data-name='$nameLower'>&#128196; $relativePath</a>"
    Out-File -FilePath $outputFile -InputObject $link -Append -Encoding utf8
}

$middleHtml = @"
        </div>
    </div>
    <div id="content">
        <h1>$title</h1>
        <p>Tài liệu này chứa toàn bộ mã nguồn của dự án (Kotlin, XML, Gradle, Markdown, JSON), được tự động trích xuất và highlight cú pháp. Bạn có thể sử dụng thanh tìm kiếm bên trái để lọc nhanh file cần xem.</p>
        <hr style="border: 1px solid #333; margin-bottom: 30px;">
"@

Out-File -FilePath $outputFile -InputObject $middleHtml -Append -Encoding utf8

Write-Host "Appending file contents..."

# Write File Contents
foreach ($file in $files) {
    $relativePath = $file.FullName.Replace((Get-Location).Path + "\", "").Replace("\", "/")
    $id = $relativePath -replace '[^a-zA-Z0-9]', '_'
    $ext = $file.Extension.Replace(".", "").ToLower()
    
    # Map extensions for Highlight.js
    if ($ext -eq "kts") { $ext = "gradle" }
    elseif ($ext -eq "kt") { $ext = "kotlin" }
    elseif ($ext -eq "pro" -or $ext -eq "properties") { $ext = "properties" }
    
    try {
        $content = Get-Content $file.FullName -Raw -Encoding UTF8
        if ($content -ne $null) {
            $escapedContent = $content.Replace("&", "&amp;").Replace("<", "&lt;").Replace(">", "&gt;").Replace("`"", "&quot;").Replace("'", "&#39;")
        } else {
            $escapedContent = "<!-- File rỗng -->"
        }
        
        $fileHtml = @"
        <div class="file-section" id="$id">
            <div class="file-header">
                <span>$relativePath</span>
                <a href="#" class="back-to-top">Lên đầu trang &uarr;</a>
            </div>
            <pre><code class="language-$ext">$escapedContent</code></pre>
        </div>
"@
        Out-File -FilePath $outputFile -InputObject $fileHtml -Append -Encoding utf8
    } catch {
        Write-Host "Error reading file: $relativePath"
    }
}

$footerHtml = @"
    </div>
    <script>
        function filterFiles() {
            var input = document.getElementById('search-box').value.toLowerCase();
            var links = document.getElementsByClassName('file-link');
            for (var i = 0; i < links.length; i++) {
                if (links[i].getAttribute('data-name').indexOf(input) > -1) {
                    links[i].style.display = "";
                } else {
                    links[i].style.display = "none";
                }
            }
        }
    </script>
</body>
</html>
"@

Out-File -FilePath $outputFile -InputObject $footerHtml -Append -Encoding utf8

Write-Host "Done! File generated at $outputFile"
