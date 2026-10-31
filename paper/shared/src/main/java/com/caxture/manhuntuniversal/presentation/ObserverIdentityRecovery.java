package com.caxture.manhuntuniversal.presentation;
final class ObserverIdentityRecovery {
    private ObserverIdentityRecovery(){ }
    static void apply(boolean concealed,Runnable update,Runnable show){update.run();if(concealed){show.run();update.run();}}
}
