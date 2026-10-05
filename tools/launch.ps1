[CmdletBinding()]
param([switch]$SkipPair, [string]$InstanceId = 'T3Craft-26.3-Native')
# Convenience alias; normal use needs no backend daemon.
& (Join-Path $PSScriptRoot 'install.ps1') -Launch -SkipPair:$SkipPair -InstanceId $InstanceId
