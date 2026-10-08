package gg.fotia.futureui.shop;

import org.bukkit.inventory.ItemStack;

/** 在副本上计算发货，背包空间不足时不发生部分交付。 */
public final class InventoryDelivery {
    private InventoryDelivery(){}
    public static long capacity(ItemStack[] contents,ItemStack item){long free=0;int limit=item.getMaxStackSize();for(ItemStack current:contents)if(current==null||current.getType().isAir())free+=limit;else if(current.isSimilar(item))free+=Math.max(0,limit-current.getAmount());return free;}
    public static ItemStack[] simulate(ItemStack[] contents,ItemStack item,int amount){
        ItemStack[] copy=new ItemStack[contents.length];for(int i=0;i<contents.length;i++)copy[i]=contents[i]==null?null:contents[i].clone();
        int remaining=amount,limit=item.getMaxStackSize();
        for(ItemStack current:copy)if(current!=null&&current.isSimilar(item)){int add=Math.min(Math.max(0,limit-current.getAmount()),remaining);current.setAmount(current.getAmount()+add);remaining-=add;}
        for(int i=0;i<copy.length&&remaining>0;i++)if(copy[i]==null||copy[i].getType().isAir()){int add=Math.min(limit,remaining);copy[i]=item.asQuantity(add);remaining-=add;}
        return remaining==0?copy:null;
    }
}
