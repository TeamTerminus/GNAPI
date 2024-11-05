package net.teamterminus.machineessentials.energy.electric.base;

import net.minecraft.block.entity.BlockEntity;
import net.modificationstation.stationapi.api.util.math.Direction;
import net.teamterminus.machineessentials.MachineEssentials;
import net.teamterminus.machineessentials.energy.electric.api.IElectric;
import net.teamterminus.machineessentials.energy.electric.api.IElectricWire;
import net.teamterminus.machineessentials.network.INetworkComponent;
import net.teamterminus.machineessentials.network.NetworkPath;
import org.jetbrains.annotations.NotNull;

public abstract class ElectricDeviceBlockEntity extends ElectricBlockEntity {

    @Override
    public boolean canReceive(@NotNull Direction dir) {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        //reset counters
        ampsUsing = 0;
        averageAmpLoad.increment(world,0);
        averageEnergyTransfer.increment(world,0);

        //try to pull max allowed current from any connected side
        for (Direction dir : Direction.values()) {
            BlockEntity be = MachineEssentials.getBlockEntity(dir, world, this);
            if(be instanceof IElectricWire){
                receiveEnergy(dir,getMaxInputAmperage());
            }
        }
    }

    @Override
    public long receiveEnergy(@NotNull Direction dir, long amperage) {
        if(amperage > getMaxInputAmperage()){
            return 0;
        }
        long remainingCapacity = getCapacityRemaining();
        long willUseAmps = 0;
        BlockEntity blockEntity = MachineEssentials.getBlockEntity(dir, world, this);
        if(blockEntity instanceof IElectricWire wire) {
            //for every known path
            for (NetworkPath path : energyNet.getPathData(wire.getPosition())) {
                long pathLoss = 0;
                //ignore itself or non-electric components in the path
                if(path.target == this || !(path.target instanceof IElectric dest)){
                    continue;
                }

                //receive/provide check
                if(dest.canProvide(path.targetDirection.getOpposite())) {
                    if (canReceive(dir)) {
                        //get max voltage from destination
                        //limit amps to maximum available from destination
                        long voltage = dest.getMaxOutputVoltage();
                        amperage = Math.min(amperage, (dest.getMaxOutputAmperage() - dest.getAmpsCurrentlyUsed()));
                        //calculate path loss
                        for (INetworkComponent component : path.path) {
                            if(component instanceof IElectricWire pathWire){
                                pathLoss += pathWire.getProperties().material().lossPerBlock();
                            }
                        }
                        if(pathLoss >= voltage){
                            //avoid paths where all energy is lost
                            continue;
                        }
                        //voltage drop
                        long pathVoltage = voltage - pathLoss;
                        boolean pathBroken = false;
                        //handle wires with insufficient voltage rating
                        for (INetworkComponent pathBlockEntity : path.path) {
                            if(pathBlockEntity instanceof IElectricWire pathWire){
                                if(pathWire.getVoltageRating() < voltage){
                                    pathWire.onOvervoltage(voltage);
                                    pathBroken = true;
                                    pathVoltage = Math.min(pathWire.getVoltageRating(), pathVoltage);
                                    break;
                                }
                            }
                        }
                        if(pathBroken) continue;

                        if(pathVoltage > 0){
                            //handle device over-voltage
                            if(pathVoltage > getMaxInputVoltage()){
                                onOvervoltage(pathVoltage);
                                return Math.max(amperage, getMaxInputAmperage() - ampsUsing); //short circuit amperage
                            }
                            if(remainingCapacity >= pathVoltage){
                                //calculate real current draw
                                willUseAmps = Math.min(remainingCapacity / pathVoltage, Math.min(amperage, getMaxInputAmperage() - ampsUsing));
                                if(willUseAmps > 0){
                                    long willUseEnergy = pathVoltage * willUseAmps;
                                    if(dest.getEnergy() >= willUseEnergy){

                                        //set current in wires
                                        for (INetworkComponent pathBlockEntity : path.path) {
                                            if (pathBlockEntity instanceof IElectricWire pathWire) {
                                                long voltageTraveled = voltage;
                                                voltageTraveled -= pathWire.getProperties().material().lossPerBlock();
                                                if (voltageTraveled <= 0) break;
                                                pathWire.incrementAmperage(willUseAmps);
                                            }
                                        }

                                        //finish energy transfer
                                        addAmpsToUse(willUseAmps);
                                        internalAddEnergy(willUseEnergy);
                                        dest.internalRemoveEnergy(willUseEnergy);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return willUseAmps; //return amps used
    }
}
