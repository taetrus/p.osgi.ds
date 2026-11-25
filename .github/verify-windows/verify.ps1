param([Parameter(Mandatory)][string]$Phase)
. "$PSScriptRoot\lib.ps1"
$ok = 'BUILD SUCCESS'

# Each check runs a Windows command exactly as a doc shows it. -Anchor is the text of the
# line in the doc. When you change one of these lines in a doc, change its anchor here too.
switch ($Phase) {
    'ps-build' {
        Invoke-Doc -Id 'V01-ps51-clean-verify' -Literal '.\mvnw.cmd clean verify' -Shell powershell -Expect $ok -TimeoutSec 3600 -Note 'README.md, build.md: .\mvnw.cmd in PowerShell' | Out-Null
        Invoke-Doc -Id 'V02-ps51-fatjar' -Literal '.\mvnw.cmd -f fatjar clean package' -Shell powershell -Expect $ok -TimeoutSec 1800 -Note 'fat-jar.md: mvnw.cmd -f fatjar clean package' | Out-Null
        Invoke-Doc -Id 'V03-pwsh-skiptests' -Literal '.\mvnw.cmd clean package -DskipTests' -Shell pwsh -Expect $ok -TimeoutSec 1800 -Note 'build.md: quick build in pwsh' | Out-Null
        Invoke-Doc -Id 'V04-cmd-readme-build' -File 'README.md' -Anchor 'mvnw.cmd clean verify' -Shell cmd -Expect $ok -TimeoutSec 3600 | Out-Null
    }
    'tests' {
        Invoke-Doc -Id 'V10-cmd-dtest-hash' -File 'docs/testing.md' -Anchor 'mvnw.cmd test -pl com.kk.pde.ds.target,com.kk.pde.ds.spike.api,com.kk.pde.ds.spike.master -Dtest="MasterAppTest#spyRunsTheRealServiceAndRecordsCalls"' -Shell cmd -Expect "(?s)Tests run: 1, Failures: 0.*$ok" -TimeoutSec 1800 | Out-Null
        foreach ($sh in 'powershell', 'pwsh') {
            Invoke-Doc -Id "V11-$sh-dtest" -File 'docs/testing.md' -Anchor '.\mvnw.cmd test -pl com.kk.pde.ds.target,com.kk.pde.ds.api,com.kk.pde.ds.imp "-Dtest=GreetHealthCheckTest"' -Shell $sh -Expect "(?s)GreetHealthCheckTest.*$ok" -TimeoutSec 1800 | Out-Null
            Invoke-Doc -Id "V12-$sh-dtest-hash" -File 'docs/testing.md' -Anchor '.\mvnw.cmd test -pl com.kk.pde.ds.target,com.kk.pde.ds.spike.api,com.kk.pde.ds.spike.master "-Dtest=MasterAppTest#spyRunsTheRealServiceAndRecordsCalls"' -Shell $sh -Expect "(?s)Tests run: 1, Failures: 0.*$ok" -TimeoutSec 1800 | Out-Null
            Invoke-Doc -Id "V13-$sh-ittest" -File 'docs/testing.md' -Anchor '.\mvnw.cmd verify -pl com.kk.pde.ds.target,com.kk.pde.ds.api,com.kk.pde.ds.imp "-Dit.test=GreetServiceIT"' -Shell $sh -Expect "(?s)Running com.kk.pde.ds.imp.GreetServiceIT.*$ok" -TimeoutSec 1800 | Out-Null
        }
    }
    'runtime' {
        $r = Start-Doc -Id 'V20-readme-start' -File 'README.md' -Anchor 'set OPENROUTER_API_KEY=your_key_here', 'distribution\scripts\run.bat' -Shell cmd
        if ($r) {
            $okS = Wait-Http -Run $r
            Show-Log $r 20
            Write-Result $r.Id $r.Where 'cmd' $okS 'product answers /api/greet'
            if ($okS) {
                foreach ($sh in 'powershell', 'pwsh') {
                    Invoke-Doc -Id "V21-$sh-convertto-json" -File 'docs/mcp-server-testing.md' -Anchor '-Body ''{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hello from powershell"}}}'' |' -Offset -2 -Count 4 -Shell $sh -Expect 'Echo: hello from powershell' | Out-Null
                }
                # Terminal plugin: token, exec without and with the token, the stream (step 3).
                $tAuth = '$auth = @{ Authorization = ''Basic '' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes(''admin:admin'')) }'
                $tToken = @($tAuth,
                    '$page = Invoke-WebRequest -UseBasicParsing -Headers $auth -Uri http://localhost:8080/system/console/terminal',
                    '$token = [regex]::Match($page.Content, ''data-token="([0-9a-f]+)"'').Groups[1].Value',
                    '$exec = ''http://localhost:8080/system/console/terminal/exec''')
                foreach ($sh in 'powershell', 'pwsh') {
                    Invoke-Doc -Id "V23-$sh-terminal-exec-stream" -File 'docs/webconsole-terminal.md' -Anchor $tAuth -Count 8 -Shell $sh -Expect '(?s)no token: 403.*with token: 202.*data: osgi> ss.*event: done' -TimeoutSec 120 | Out-Null
                }
                # Terminal plugin: echo of non-ASCII text through the shell output path (step 5).
                # The anchors are the first four lines of step 3 and the four lines of step 5.
                $tUmlauts = $tToken + @(
                    '$umlauts = [string][char]0xE4 + [char]0xF6 + [char]0xFC',
                    '$u = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $exec -Headers ($auth + @{ ''X-Terminal-Token'' = $token }) -ContentType ''text/plain; charset=utf-8'' -Body ([Text.Encoding]::UTF8.GetBytes("echo $umlauts"))',
                    'curl.exe -s -N --max-time 5 -u admin:admin -o "$env:TEMP\terminal-stream.txt" http://localhost:8080/system/console/terminal/stream',
                    '"echo status: $($u.StatusCode), umlauts on the stream: $((Get-Content -Encoding UTF8 "$env:TEMP\terminal-stream.txt") -ccontains "data: $umlauts")"')
                foreach ($sh in 'powershell', 'pwsh') {
                    Invoke-Doc -Id "V24-$sh-terminal-utf8" -File 'docs/webconsole-terminal.md' -Anchor $tUmlauts -Shell $sh -Expect 'echo status: 202, umlauts on the stream: True' -TimeoutSec 120 | Out-Null
                }
            }
        }
        Stop-AllJava
        $ws = 'C:\ws-fixture'
        New-Item -ItemType Directory -Force "$ws\.metadata\.plugins\org.eclipse.core.resources\.projects\com.kk.pde.ds.api" | Out-Null
        Copy-Item -Recurse -Force "$Repo\com.kk.pde.ds.api" "$ws\com.kk.pde.ds.api"
        Invoke-Doc -Id 'V22-pde2tycho-scan' -File 'docs/pde-to-tycho-migration.md' -Anchor 'tools\pde2tycho\pde2tycho.bat scan <workspace> -o migration.json' -Replace @{ '<workspace>' = $ws } -Shell cmd -Expect 'Scanned 1 project' -TimeoutSec 900 | Out-Null
    }
    'resume' {
        Invoke-Doc -Id 'V30-install' -Literal 'mvnw.cmd clean install -DskipTests' -Shell cmd -Expect $ok -TimeoutSec 1800 -Note 'harness: install first' | Out-Null
        Invoke-Doc -Id 'V31-rf-after-install' -File 'docs/build.md' -Anchor './mvnw verify -rf :distribution' -Replace @{ './mvnw' = 'mvnw.cmd' } -Shell cmd -Expect $ok -TimeoutSec 1800 | Out-Null
    }
}
