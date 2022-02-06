package net.coreprotect.utility;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Player;

import net.coreprotect.model.BlockGroup;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class Teleport {

    private Teleport() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean performSafeTeleport(Player player, Location targetBlockLocation, boolean enforceTeleport) {
        try {

            //check bypass tp conditions
           /* if( player.getGameMode() == GameMode.SPECTATOR || enforceTeleport   ) {
                player.sendMessage("Bypass condition detected" + enforceTeleport);
                player.teleport(targetBlockLocation);
                return true;
            }*/





            Block primaryTargetBlock = targetBlockLocation.getBlock();
            BlockData primaryTargetBlockData = primaryTargetBlock.getBlockData();
            Set <Location> targetBlockLocations = new LinkedHashSet<>();
            targetBlockLocations.add(targetBlockLocation.getBlock().getLocation());


            if (( primaryTargetBlockData instanceof Chest) && ((Chest) primaryTargetBlockData).getType() != Chest.Type.SINGLE) {
                Chest.Type chestType = ((Chest) primaryTargetBlockData).getType();
                Block relativeBlock = ChestTool.getDoubleChestRelative(primaryTargetBlock, primaryTargetBlockData, chestType);
                targetBlockLocations.add(relativeBlock.getLocation());

            }


            Set <Vector> surroundingLocations = new LinkedHashSet<>();
            Set <BlockFace> surroundingLocationFaces = new LinkedHashSet<>();

            Block targetBlock;
            Block relativeBlock;
            BlockData relativeBlockData;
            Vector blockDelta;
            for (Location tgtBlockLocation: targetBlockLocations) {  //rename
                targetBlock = tgtBlockLocation.getBlock();

                BlockData targetBlockData = targetBlock.getBlockData();


                if (targetBlockData instanceof Directional) {

                    surroundingLocationFaces.add(((Directional) targetBlockData).getFacing());
                }
                surroundingLocationFaces.add(BlockFace.NORTH);
                surroundingLocationFaces.add(BlockFace.SOUTH);
                surroundingLocationFaces.add(BlockFace.EAST);
                surroundingLocationFaces.add(BlockFace.WEST);

                for(int deltaDirection = 1; deltaDirection <= 3; deltaDirection ++) {

                    for (BlockFace faces : surroundingLocationFaces) {
                        blockDelta = targetBlock.getRelative(faces,deltaDirection).getLocation().toVector().subtract(tgtBlockLocation.toVector());
                        relativeBlock = targetBlock.getRelative(faces);
                        relativeBlockData = relativeBlock.getBlockData();
                        if( relativeBlockData.getMaterial().equals(Material.AIR) ||
                                relativeBlockData.getMaterial().equals(Material.WATER) ||
                                relativeBlockData instanceof Slab ||
                                relativeBlockData instanceof Stairs) {

                            surroundingLocations.add(blockDelta);
                        }


                    }
                }


                Location testLocation;
                Location deltaLocation;
                for (Vector cardinals : surroundingLocations) {
                    testLocation = targetBlock.getLocation().clone().add(cardinals.toLocation(targetBlock.getWorld()));

                    if(!targetBlockLocations.contains(testLocation)) {


                        //-1 to 5
                        for (int dy = -4; dy <= 1; dy++) {

                            deltaLocation = testLocation.clone().add(new Vector(0, dy, 0));

                            if (Teleport.teleportIfSafe(player, deltaLocation, tgtBlockLocation,true)) return true;


                        }
                    }


                }

            }





            //check on top of the block and continue to world height
            Location proposedLocation = targetBlockLocation.clone();
            for (Location tgtBlockLocation: targetBlockLocations) {
                for (int y = 0;  y < tgtBlockLocation.getWorld().getMaxHeight()- tgtBlockLocation.getBlockY(); y++) {
                    proposedLocation.setY(targetBlockLocation.clone().getY() + y);
                    if (Teleport.teleportIfSafe(player, proposedLocation, targetBlockLocation, false)) return true;
                }
            }

        }





        catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static Vector getDelta(Location middleProposedLocation, Location targetBlockLocation) {

        Vector distance = middleProposedLocation.clone().toVector().subtract(targetBlockLocation.clone().toVector());

        double x = distance.getX()-.5;
        double z = distance.getZ()-.5;

        double targetMiddleFacingX = 0;
        double targetMiddleFacingZ = 0;

        double middleProposedLocationX = middleProposedLocation.getX();
        double middleProposedLocationZ = middleProposedLocation.getZ();
        double middleProposedLocationY = middleProposedLocation.getY();
        double deltaX;
        double deltaY;
        double deltaZ;

        BoundingBox targetBoundingBox = targetBlockLocation.getBlock().getBoundingBox();

        double targetMiddleFacingY = targetBoundingBox.getCenterY();

        if (z > x && z > -x ||      //player south of block, looking +Z north at it. player yaw 135 [-180] -135
                z > 0 && x == 0    ) {  //player on axis, -180
            targetMiddleFacingX = targetBoundingBox.getCenterX();
            targetMiddleFacingZ = targetBoundingBox.getMaxZ();
            Bukkit.getLogger().info("South");

        } else if (z < x && z < -x ||   //player north of block, looking -Z south at it. player yaw -45 [0] 45
                z < 0 && x == 0    ) { //player on axis, 0
            targetMiddleFacingX = targetBoundingBox.getCenterX();
            targetMiddleFacingZ = targetBoundingBox.getMinZ();
            Bukkit.getLogger().info("North");

        } else if (x > z && x > -z ||       //player west of block, looking +X  east at it. player yaw -135 [-90] -45
                x > 0 && z == 0    ) {   //player on axis, -90
            targetMiddleFacingX = targetBoundingBox.getMaxX();
            targetMiddleFacingZ = targetBoundingBox.getCenterZ();
            Bukkit.getLogger().info("West");

        } else if (x < z && x < -z ||       //player east of block, looking -X west at it. player yaw 135 [-180] -135
                x < 0 && z ==0    ) {    //player on axis, -180
            targetMiddleFacingX = targetBoundingBox.getMinX();
            targetMiddleFacingZ = targetBoundingBox.getCenterZ();
            Bukkit.getLogger().info("East");

        }



        deltaX = middleProposedLocationX - targetMiddleFacingX;
        deltaY = middleProposedLocationY - targetMiddleFacingY;
        deltaZ = middleProposedLocationZ - targetMiddleFacingZ;
        return new Vector(deltaX, deltaY, deltaZ);
    }



    private static Location calculatePitchAndYaw(Location proposedLocation, Location targetBlockLocation)
    {
        double exactYPosition = proposedLocation.getBlockY();
        double yaw;
        double pitch = 0;


        Vector midBlock = new Vector(0.5, 0, 0.5);
        Location middleProposedLocation = proposedLocation.clone().add(midBlock);
        Location calculatedLocation = middleProposedLocation;




        if (proposedLocation.getBlockX() == targetBlockLocation.getBlockX() && proposedLocation.getBlockZ() == targetBlockLocation.getBlockZ()) {

            if (proposedLocation.getBlockY() - targetBlockLocation.getBlockY() > 0) { //positive
                pitch = 90.0; //above


            } else if (proposedLocation.getBlockY() - targetBlockLocation.getBlockY() < 0) {
                pitch = -90; //under

            }

            calculatedLocation.setPitch((float) pitch);
        }
        else {

            RayTraceResult raytraceYHit = middleProposedLocation.getWorld().rayTraceBlocks(middleProposedLocation, new Vector(0, -1, 0), 2, FluidCollisionMode.NEVER);
            if (raytraceYHit != null)
                exactYPosition = raytraceYHit.getHitPosition().getY();


            if (exactYPosition != 0)
                exactYPosition = Math.round(exactYPosition * 10.0) / 10.0;

            calculatedLocation.setY(exactYPosition);
            Vector delta = getDelta(calculatedLocation,targetBlockLocation);

            Vector deltaHead = delta.clone();
            deltaHead.add(new Vector(0,1.625,0));  //head position

            double hypotenuse = Math.sqrt(Math.pow(Math.abs(delta.getX()), 2) + Math.pow(Math.abs(delta.getZ()), 2));

            pitch = Math.toDegrees(Math.atan(deltaHead.getY() / hypotenuse));
            yaw = Math.toDegrees(Math.atan2(delta.getX(), delta.getZ() * -1));

            calculatedLocation.setPitch((float)pitch);
            calculatedLocation.setYaw((float)yaw);

        }


        return calculatedLocation;
    }


    private static boolean teleportIfSafe(Player player, Location proposedLocation, Location targetBlockLocation, boolean lineOfSightRequired)
    {
        Set<Material> unsafeBlocks = new HashSet<>(Arrays.asList(Material.LAVA));
        unsafeBlocks.addAll(BlockGroup.FIRE);
        Set<Material> unwantedGroundMaterial = new HashSet<>(Arrays.asList(Material.AIR, Material.WATER, Material.BIG_DRIPLEAF, Material.SMALL_DRIPLEAF, Material.COBWEB, Material.TNT));

        int teleportX = proposedLocation.getBlockX();
        int teleportY = proposedLocation.getBlockY();
        int teleportZ = proposedLocation.getBlockZ();
        Block body = proposedLocation.getWorld().getBlockAt(teleportX, teleportY, teleportZ);
        Block head = proposedLocation.getWorld().getBlockAt(teleportX, teleportY + 1, teleportZ);
        Block ground = proposedLocation.getWorld().getBlockAt(teleportX, teleportY -1, teleportZ);
        Material bodyMaterial = body.getType();
        Material headMaterial = head.getType();
        Material groundMaterial = ground.getType();

        if (    !Util.solidBlock(bodyMaterial) &&
                !unsafeBlocks.contains(bodyMaterial) && // Body not going to tp in a solid block or lava/fire
                !Util.solidBlock(headMaterial) &&
                !unsafeBlocks.contains(headMaterial) && // Head not going to tp in a solid block or lava/fire
                !unsafeBlocks.contains(groundMaterial)  && // Check ground for lava/fire
                !unwantedGroundMaterial.contains(groundMaterial) && //check ground is not unsafe
                teleportY <= 256            ) //make sure to not tp too far above
        {

            Location proposedWithPitchAndYaw = calculatePitchAndYaw(proposedLocation, targetBlockLocation);

            Location headLocation = proposedWithPitchAndYaw.clone().add(0,1.625,0);
            double distance = headLocation.distance(targetBlockLocation);

            if(distance > 1 && lineOfSightRequired) {
                double pitch = ((proposedWithPitchAndYaw.getPitch() + 90) * Math.PI) / 180;
                double yaw = ((proposedWithPitchAndYaw.getYaw() + 90) * Math.PI) / 180;
                double x = Math.sin(pitch) * Math.cos(yaw);
                double y = Math.sin(pitch) * Math.sin(yaw);
                double z = Math.cos(pitch);
                Vector lookingDir = new Vector(x, z, y);


                RayTraceResult intersection = proposedLocation.getWorld().rayTraceBlocks(
                        headLocation,
                        lookingDir,
                        distance + .5,
                        FluidCollisionMode.NEVER);
                if (intersection != null) {
                    if(intersection.getHitBlock().getLocation().equals(targetBlockLocation))
                    {
                        Bukkit.getLogger().info("success, intersection: " + intersection.getHitBlock());
                        player.teleport(proposedWithPitchAndYaw);
                        return true;
                    }
                    else {
                        Bukkit.getLogger().info("false, intersection: " + intersection.getHitBlock());
                        return false;
                    }

                }
            }
            else {

                player.teleport(proposedWithPitchAndYaw);
                return true;
            }

        }




        else {
            return false;
        }



        return false;
    }


}

