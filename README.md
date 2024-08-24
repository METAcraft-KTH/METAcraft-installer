# METAcraft-installer
An Installer that installs Fabric and recommended mods for METAcraft.

Fork of [FabricMC/fabric-installer](https://github.com/FabricMC/fabric-installer).

## How to update / change Minecraft and mod versions
Go to the file `installer.json` and change the values for the Minecraft version and Fabric loader version.

Also go to Modrinth and find the new versions of the mods. Find the Download button for the version, right click and copy link. Use that link as the download link.

To skip a mod (for example if Sodium hasn't updated yet) then set `enabled` to `false` and it will not show up.