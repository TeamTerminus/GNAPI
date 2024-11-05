package net.teamterminus.machineessentials.energy.electric.base;

import lombok.Getter;
import net.minecraft.block.entity.BlockEntity;
import net.modificationstation.stationapi.api.util.math.Direction;
import net.modificationstation.stationapi.api.util.math.Vec3i;
import net.teamterminus.machineessentials.MachineEssentials;
import net.teamterminus.machineessentials.energy.electric.api.IElectric;
import net.teamterminus.machineessentials.energy.electric.api.IElectricWire;
import net.teamterminus.machineessentials.energy.electric.api.WireProperties;
import net.teamterminus.machineessentials.network.Network;
import net.teamterminus.machineessentials.network.NetworkType;
import net.teamterminus.machineessentials.util.AveragingCounter;
import net.teamterminus.machineessentials.util.IBlockEntityInit;

public abstract class ElectricWireBlockEntity extends BlockEntity implements IBlockEntityInit, IElectricWire {

    public Network energyNet;
    @Getter
    protected WireProperties properties;
    protected long voltageRating = 0;
    protected long ampRating = 0;

    protected AveragingCounter averageAmpLoad = new AveragingCounter();
    @Getter
    protected long temperature = 0;

    @Override
    public NetworkType getType() {
        return NetworkType.ELECTRIC;
    }

    @Override
    public Vec3i getPosition() {
        return new Vec3i(x,y,z);
    }

    @Override
    public boolean isntConnected(Direction direction) {
        return !(MachineEssentials.getBlockEntity(direction,world,this) instanceof IElectric) && !(MachineEssentials.getBlockEntity(direction,world,this) instanceof IElectricWire);
    }

    @Override
    public void networkChanged(Network network) {
        this.energyNet = network;
    }

    @Override
    public void removedFromNetwork(Network network) {
        this.energyNet = null;
    }

    @Override
    public long getVoltageRating() {
        return voltageRating;
    }
    @Override
    public long getAmpRating() {return ampRating;}

    @Override
    public void incrementAmperage(long amps){
        averageAmpLoad.increment(world,amps);
        int dif = (int) (averageAmpLoad.getLast(world) - getAmpRating());
        if (dif > 0) {
            onOvercurrent();
        }
    }

    public double getAverageAmpLoad(){
        return averageAmpLoad.getAverage(world);
    }
}