package net.wurstclient.neoraven.music;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MusicSessionTest {
  @AfterEach void cleanup() { NeteaseHttp.clear(); }
  @Test void cancelledOrLoggedOutQrResponsesCannotRestoreSession() {
    NeteaseHttp.clear();
    long cancelled=NeteaseHttp.sessionVersion();
    NeteaseHttp.invalidatePendingCookies();
    NeteaseHttp.storeAllIfCurrent("MUSIC_U=test",cancelled);
    assertFalse(NeteaseHttp.loggedIn());
    long current=NeteaseHttp.sessionVersion();
    NeteaseHttp.storeAllIfCurrent("MUSIC_U=test; __csrf=example",current);
    assertTrue(NeteaseHttp.loggedIn());
    NeteaseHttp.logout();
    NeteaseHttp.storeAllIfCurrent("MUSIC_U=test",current);
    assertFalse(NeteaseHttp.loggedIn());
    assertEquals("",NeteaseHttp.csrf());
  }
}
