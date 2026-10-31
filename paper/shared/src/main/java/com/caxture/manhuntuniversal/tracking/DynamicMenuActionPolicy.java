package com.caxture.manhuntuniversal.tracking;
final class DynamicMenuActionPolicy {
    private DynamicMenuActionPolicy(){ }
    static boolean valid(boolean sameWindow,long renderedDirectory,long currentDirectory,boolean dismiss){return sameWindow&&(dismiss||renderedDirectory==currentDirectory);}
    static boolean renew(long renderedAt,long now){return now-renderedAt>=240_000_000_000L;}
}
