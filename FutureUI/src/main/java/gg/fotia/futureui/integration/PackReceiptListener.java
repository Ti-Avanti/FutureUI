package gg.fotia.futureui.integration;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientResourcePackStatus;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientResourcePackStatus;
import gg.fotia.futureui.asset.PackState;
import java.util.UUID;

/** 观察配置及游戏两个阶段的资源包回执，兼容外部后端，不修改任何数据包。 */
public final class PackReceiptListener extends PacketListenerAbstract implements AutoCloseable {
    private final PackState state;
    public PackReceiptListener(PackState state){super(PacketListenerPriority.MONITOR);this.state=state;PacketEvents.getAPI().getEventManager().registerListener(this);}
    @Override public void onPacketReceive(PacketReceiveEvent event){
        UUID player=event.getUser().getUUID();if(player==null)return;
        if(event.getPacketType()==PacketType.Configuration.Client.RESOURCE_PACK_STATUS){
            var packet=new WrapperConfigClientResourcePackStatus(event);state.receipt(player,packet.getPackId(),packet.getResult().name());
        }else if(event.getPacketType()==PacketType.Play.Client.RESOURCE_PACK_STATUS){
            var packet=new WrapperPlayClientResourcePackStatus(event);state.receipt(player,packet.getPackId(),packet.getResult().name());
        }
    }
    @Override public void close(){PacketEvents.getAPI().getEventManager().unregisterListener(this);}
}
