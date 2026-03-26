param(
    [Parameter(Mandatory = $true)]
    [string]$CommitMessage,
    [string]$Version
)

$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $true

function Get-NextPatchVersion {
    $latestTag = (git tag --list "v*" --sort=version:refname | Select-Object -Last 1)
    if (-not $latestTag) {
        return "v1.0.0"
    }

    if ($latestTag -notmatch '^v(\d+)\.(\d+)\.(\d+)$') {
        throw "Latest tag '$latestTag' does not follow vMAJOR.MINOR.PATCH."
    }

    $major = [int]$matches[1]
    $minor = [int]$matches[2]
    $patch = [int]$matches[3] + 1
    return "v$major.$minor.$patch"
}

function Invoke-Step {
    param(
        [Parameter(Mandatory = $true)]
        [scriptblock]$Action,
        [Parameter(Mandatory = $true)]
        [string]$ErrorMessage
    )

    & $Action
    if ($LASTEXITCODE -ne 0) {
        throw $ErrorMessage
    }
}

if (-not $Version) {
    $Version = Get-NextPatchVersion
}

if ($Version -notmatch '^v\d+\.\d+\.\d+$') {
    throw "Version must follow vMAJOR.MINOR.PATCH."
}

$javaHome = "C:\Program Files\Android\Android Studio\jbr"
if (Test-Path $javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

Write-Host "Building debug and release APKs..."
Invoke-Step -Action { .\gradlew.bat assembleDebug assembleRelease } -ErrorMessage "Gradle build failed."

Write-Host "Staging changes..."
Invoke-Step -Action { git add -A } -ErrorMessage "git add failed."

$status = git status --short
if (-not $status) {
    Write-Host "No changes to commit. Skipping commit."
} else {
    Write-Host "Creating commit..."
    Invoke-Step -Action { git commit -m $CommitMessage } -ErrorMessage "git commit failed."
}

$currentBranch = git branch --show-current
if (-not $currentBranch) {
    throw "Could not determine current git branch."
}

Write-Host "Pushing branch $currentBranch..."
Invoke-Step -Action { git push origin $currentBranch } -ErrorMessage "git push failed."

$existingTag = git tag --list $Version
if ($existingTag) {
    throw "Tag $Version already exists."
}

Write-Host "Creating tag $Version..."
Invoke-Step -Action { git tag $Version } -ErrorMessage "git tag failed."

Write-Host "Pushing tag $Version..."
Invoke-Step -Action { git push origin $Version } -ErrorMessage "git push tag failed."

Write-Host ""
Write-Host "Release triggered for $Version."
Write-Host "GitHub Release workflow should attach APK assets automatically."
