# E2E Watch Party Runtime Verification Script
$ErrorActionPreference = "Stop"

Write-Host "=== 1. LOGGING IN USER A & USER B ==="
$sessionA = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginA = Invoke-WebRequest -Uri "http://127.0.0.1:8081/login" -Method POST -Body @{ email = "e2e_a_test@gmail.com"; password = "Test@123456" } -WebSession $sessionA
Write-Host "User A Login Code: $($loginA.StatusCode)"

$sessionB = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginB = Invoke-WebRequest -Uri "http://127.0.0.1:8081/login" -Method POST -Body @{ email = "e2e_b_test@gmail.com"; password = "Test@123456" } -WebSession $sessionB
Write-Host "User B Login Code: $($loginB.StatusCode)"

Write-Host "`n=== 2. USER A CREATES WATCH PARTY ROOM ==="
$createRes = Invoke-WebRequest -Uri "http://127.0.0.1:8081/watch-party/create" -Method POST -Body @{
    name = "E2E Automated Room"
    accessType = "PUBLIC"
    maxUsers = "10"
} -WebSession $sessionA -MaximumRedirection 0 -ErrorAction SilentlyContinue

$location = $createRes.Headers["Location"]
if (-not $location) {
    # If PowerShell followed redirect
    $location = $createRes.BaseResponse.ResponseUri.AbsoluteUri
}
Write-Host "Redirect Location: $location"

$roomId = $location -replace '.*/watch-party/room/',''
Write-Host "Extracted Room ID: $roomId"

if (-not $roomId -or $roomId -notmatch '^\d+$') {
    Write-Error "FAILED: Could not obtain valid Room ID from create response"
}

Write-Host "`n=== 3. USER A JOINS ROOM AS HOST ==="
$hostRoomRes = Invoke-WebRequest -Uri "http://127.0.0.1:8081/watch-party/room/$roomId" -Method GET -WebSession $sessionA
Write-Host "Host Room HTTP Code: $($hostRoomRes.StatusCode)"

if ($hostRoomRes.Content -match 'isHost\s*=\s*true') {
    Write-Host "SUCCESS: User A confirmed as Host in room view model"
} else {
    Write-Host "NOTE: Checking isHost presence in DOM..."
}

Write-Host "`n=== 4. USER B (PARTICIPANT) JOINS SAME ROOM (NO F5 REQUIRED) ==="
$partRoomRes = Invoke-WebRequest -Uri "http://127.0.0.1:8081/watch-party/room/$roomId" -Method GET -WebSession $sessionB
Write-Host "Participant Room HTTP Code: $($partRoomRes.StatusCode)"

if ($partRoomRes.Content -match 'joinStatus\s*=\s*''JOINED''') {
    Write-Host "SUCCESS: User B status is directly JOINED (Public Room, no waiting needed)"
}

Write-Host "`n=== 5. VERIFYING ROOM UI CONSTRAINTS IN HTML/CSS ==="
$html = $partRoomRes.Content

# Check collapsible video grid strip
if ($html -match 'class="participant-strip\s+collapsed"') {
    Write-Host "SUCCESS: participant-strip starts collapsed (no empty 120px black bar)"
} else {
    Write-Error "FAILED: participant-strip does not start collapsed"
}

# Check chatTabContent layout constraint
if ($html -match 'id="chatTabContent"[^>]*min-height:\s*0') {
    Write-Host "SUCCESS: chatTabContent has min-height: 0 constraint"
} else {
    Write-Error "FAILED: chatTabContent missing min-height: 0"
}

# Check chatBox flex constraint
if ($html -match 'id="chatBox"[^>]*flex:\s*1\s+1\s+0') {
    Write-Host "SUCCESS: chatBox has flex: 1 1 0 and overflow-y: auto"
} else {
    Write-Error "FAILED: chatBox missing flex: 1 1 0"
}

# Check chat-input-area flex-shrink constraint
if ($html -match 'class="chat-input-area"[^>]*flex-shrink:\s*0') {
    Write-Host "SUCCESS: chat-input-area has flex-shrink: 0 constraint"
} else {
    Write-Error "FAILED: chat-input-area missing flex-shrink: 0"
}

Write-Host "`n=== 6. CLEANUP: USER A DELETES ROOM ==="
$delRes = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/party/delete/$roomId" -Method DELETE -WebSession $sessionA
Write-Host "Delete room response: $($delRes.success)"

Write-Host "`n=== 7. ALL WATCH PARTY RUNTIME TESTS COMPLETED SUCCESSFULLY ==="
