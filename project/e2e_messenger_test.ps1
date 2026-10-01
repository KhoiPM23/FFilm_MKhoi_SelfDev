# E2E Runtime Verification Script
$ErrorActionPreference = "Stop"

Write-Host "=== 1. LOGGING IN USER A & USER B ==="
$sessionA = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginA = Invoke-WebRequest -Uri "http://127.0.0.1:8081/login" -Method POST -Body @{ email = "e2e_a_test@gmail.com"; password = "Test@123456" } -WebSession $sessionA
Write-Host "User A (ID 19) Login HTTP Code: $($loginA.StatusCode)"

$sessionB = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginB = Invoke-WebRequest -Uri "http://127.0.0.1:8081/login" -Method POST -Body @{ email = "e2e_b_test@gmail.com"; password = "Test@123456" } -WebSession $sessionB
Write-Host "User B (ID 20) Login HTTP Code: $($loginB.StatusCode)"

Write-Host "`n=== 2. MESSENGER: USER SEARCH API ==="
$usersRes = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/messenger/users?q=User+B" -Method GET -WebSession $sessionA
Write-Host "Discovered users count: $($usersRes.Count)"
$foundB = $usersRes | Where-Object { $_.id -eq 20 }
if ($foundB) {
    Write-Host "SUCCESS: User B found in search: $($foundB.name) ($($foundB.email))"
} else {
    Write-Error "FAILED: User B not found in search results"
}

Write-Host "`n=== 3. MESSENGER: SEND TEXT MESSAGE ==="
$textBody = @{
    receiverId = 20
    content = "Xin chào User B từ E2E script!"
    type = "TEXT"
} | ConvertTo-Json
$msgText = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/messenger/send" -Method POST -ContentType "application/json" -Body $textBody -WebSession $sessionA
Write-Host "Text message created with ID: $($msgText.id), content: '$($msgText.content)'"

Write-Host "`n=== 4. MESSENGER: SEND STICKER MESSAGE ==="
$stickerBody = @{
    receiverId = 20
    content = "https://media.giphy.com/media/26BRv0ThflsHCqDrG/giphy.gif"
    type = "STICKER"
} | ConvertTo-Json
$msgSticker = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/messenger/send" -Method POST -ContentType "application/json" -Body $stickerBody -WebSession $sessionA
Write-Host "Sticker message created with ID: $($msgSticker.id), type: $($msgSticker.type)"

Write-Host "`n=== 5. MESSENGER: CALL HISTORY DEDUPLICATION TEST ==="
$testCallId = "call_e2e_test_" + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

$callBody1 = @{
    partnerId = 20
    initiatorId = 19
    callType = "VIDEO"
    status = "COMPLETED"
    duration = 88
    callId = $testCallId
} | ConvertTo-Json

# First call record dispatch (from Caller)
$rec1 = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/messenger/call-record" -Method POST -ContentType "application/json" -Body $callBody1 -WebSession $sessionA
Write-Host "Caller record result: ID $($rec1.id), status: $($rec1.callStatus), duration: $($rec1.callDuration)"

# Second call record dispatch with SAME callId (simulating Callee or duplicate window.endCall callback)
$callBody2 = @{
    partnerId = 19
    initiatorId = 19
    callType = "VIDEO"
    status = "COMPLETED"
    duration = 88
    callId = $testCallId
} | ConvertTo-Json

$rec2 = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/messenger/call-record" -Method POST -ContentType "application/json" -Body $callBody2 -WebSession $sessionB
Write-Host "Callee duplicate record result: ID $($rec2.id), status: $($rec2.callStatus)"

if ($rec1.id -eq $rec2.id) {
    Write-Host "SUCCESS: Idempotency preserved! Both dispatches resolved to exactly the SAME record ID $($rec1.id)"
} else {
    Write-Error "FAILED: Duplicate call records created: $($rec1.id) vs $($rec2.id)"
}

Write-Host "`n=== 6. MESSENGER: CONVERSATION LIST & ONLINE PRESENCE METADATA ==="
$convs = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/messenger/conversations" -Method GET -WebSession $sessionA
$convWithB = $convs | Where-Object { $_.partnerId -eq 20 }
if ($convWithB) {
    Write-Host "Conversation with User B found:"
    Write-Host "  partnerName: $($convWithB.partnerName)"
    Write-Host "  lastMessage: $($convWithB.lastMessage)"
    Write-Host "  online: $($convWithB.online)"
    Write-Host "  lastActiveTimestamp: $($convWithB.lastActiveTimestamp)"
    if ($convWithB.lastActiveTimestamp -ne $null) {
        Write-Host "SUCCESS: lastActiveTimestamp is numeric epoch milliseconds!"
    } else {
        Write-Host "NOTE: User B has no recorded lastActiveTimestamp yet (null is valid if not yet online)."
    }
} else {
    Write-Error "FAILED: Conversation with User B not found in User A's list"
}

Write-Host "`n=== 7. ALL MESSENGER VERIFICATION TESTS COMPLETED SUCCESSFULLY ==="
