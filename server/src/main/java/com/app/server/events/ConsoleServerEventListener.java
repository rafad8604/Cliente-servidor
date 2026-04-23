package com.app.server.events;

/**
 * Listener inicial de eventos: imprime por consola con formato uniforme.
 * Sirve como punto de extensión para otros listeners (UI, DB, etc.).
 */
public class ConsoleServerEventListener implements ServerEventListener {

    @Override
    public void onEvent(ServerEvent event) {
        System.out.println(event.toString());
    }
}
