# Environnement de dev local (PowerShell). Utilise les outils installés s'ils sont dans le PATH,
# sinon les versions portables de %USERPROFILE%\tools. Surchargez via JAVA_HOME_21 / NODE_HOME.
$tools = Join-Path $env:USERPROFILE "tools"

$jdk = if ($env:JAVA_HOME_21) { $env:JAVA_HOME_21 } else { Get-ChildItem $tools -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName }
if ($jdk) { $env:JAVA_HOME = $jdk; $env:Path = "$jdk\bin;$env:Path" }

$node = if ($env:NODE_HOME) { $env:NODE_HOME } else { Get-ChildItem $tools -Directory -Filter "node-v2*" -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName }
if ($node) { $env:Path = "$node;$env:Path" }

# Python 3.11 : lanceur py si disponible, sinon python3/python
$PythonExe = "python"
if (Get-Command py -ErrorAction SilentlyContinue) {
    & py -3.11 --version *> $null
    if ($LASTEXITCODE -eq 0) { $PythonExe = "py -3.11" }
}
if ($PythonExe -eq "python" -and (Get-Command python3 -ErrorAction SilentlyContinue)) { $PythonExe = "python3" }
