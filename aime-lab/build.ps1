param(
    [Parameter(Mandatory=$true)][string]$AndroidJar,
    [Parameter(Mandatory=$true)][string]$BuildTools,
    [Parameter(Mandatory=$true)][string]$JdkRoot
)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$out = Join-Path $projectRoot 'build'
$javac = Join-Path $JdkRoot 'bin\javac.exe'
$java = Join-Path $JdkRoot 'bin\java.exe'
$jar = Join-Path $JdkRoot 'bin\jar.exe'
$keytool = Join-Path $JdkRoot 'bin\keytool.exe'
$aapt = Join-Path $BuildTools 'aapt2.exe'
$zipalign = Join-Path $BuildTools 'zipalign.exe'
$signer = Join-Path $BuildTools 'lib\apksigner.jar'
$d8 = Join-Path $BuildTools 'lib\d8.jar'
foreach ($required in @($AndroidJar,$javac,$java,$jar,$keytool,$aapt,$zipalign,$signer,$d8)) {
    if (!(Test-Path -LiteralPath $required)) { throw "Missing build input: $required" }
}
foreach ($directory in @($out,"$out\classes","$out\test","$out\dex","$out\generated")) {
    New-Item -ItemType Directory -Force $directory | Out-Null
}
function Run-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed: $LASTEXITCODE" }
}
Run-Checked $javac @('-encoding','UTF-8','--release','8','-d',"$out\test", "$projectRoot\src\dev\stellium\aimelab\FelicaProtocol.java", "$projectRoot\test\FelicaProtocolTest.java")
Run-Checked $java @('-cp',"$out\test",'FelicaProtocolTest')
Run-Checked $aapt @('compile','--dir',"$projectRoot\res",'-o',"$out\resources.zip")
Run-Checked $aapt @('link','-o',"$out\unsigned.apk",'-I',$AndroidJar,'--manifest',"$projectRoot\AndroidManifest.xml",'--java',"$out\generated",'--min-sdk-version','24','--target-sdk-version','35','--version-code','1','--version-name','0.1-lab',"$out\resources.zip")
$sources = @(Get-ChildItem "$projectRoot\src","$out\generated" -Recurse -Filter '*.java' | ForEach-Object FullName)
Run-Checked $javac (@('-encoding','UTF-8','-source','8','-target','8','-Xlint:-options','-classpath',$AndroidJar,'-d',"$out\classes") + $sources)
$classes = @(Get-ChildItem "$out\classes" -Recurse -Filter '*.class' | ForEach-Object FullName)
Run-Checked $java (@('-cp',$d8,'com.android.tools.r8.D8','--lib',$AndroidJar,'--min-api','24','--output',"$out\dex") + $classes)
Run-Checked $jar @('uf',"$out\unsigned.apk",'-C',"$out\dex",'classes.dex')
Run-Checked $zipalign @('-f','-p','4',"$out\unsigned.apk", "$out\aligned.apk")
$key = Join-Path $out 'lab-signing.keystore'
if (!(Test-Path -LiteralPath $key)) {
    Run-Checked $keytool @('-genkeypair','-keystore',$key,'-storepass','android','-alias','aimelab','-keypass','android','-dname','CN=Aime Mobile Lab Development','-keyalg','RSA','-keysize','2048','-validity','3650')
}
Run-Checked $java @('-jar',$signer,'sign','--ks',$key,'--ks-pass','pass:android','--key-pass','pass:android','--out',"$out\AimeMobileLab.apk", "$out\aligned.apk")
Run-Checked $java @('-jar',$signer,'verify','--verbose',"$out\AimeMobileLab.apk")
Run-Checked $zipalign @('-c','4',"$out\AimeMobileLab.apk")
Get-FileHash "$out\AimeMobileLab.apk" -Algorithm SHA256
