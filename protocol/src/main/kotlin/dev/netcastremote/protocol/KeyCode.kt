package dev.netcastremote.protocol

/**
 * Virtual keys from UDAP 2.0 Annex A. The TV maps these to its own scan codes.
 * Color keys are stored in remote order (red, green, yellow, blue), which is
 * not the numeric order of the codes.
 */
enum class KeyCode(
    val code: Int,
    val label: String,
    val family: Family,
) {
    POWER(1, "Power", Family.POWER),
    NUM_0(2, "0", Family.DIGIT),
    NUM_1(3, "1", Family.DIGIT),
    NUM_2(4, "2", Family.DIGIT),
    NUM_3(5, "3", Family.DIGIT),
    NUM_4(6, "4", Family.DIGIT),
    NUM_5(7, "5", Family.DIGIT),
    NUM_6(8, "6", Family.DIGIT),
    NUM_7(9, "7", Family.DIGIT),
    NUM_8(10, "8", Family.DIGIT),
    NUM_9(11, "9", Family.DIGIT),
    UP(12, "Up", Family.NAV),
    DOWN(13, "Down", Family.NAV),
    LEFT(14, "Left", Family.NAV),
    RIGHT(15, "Right", Family.NAV),
    OK(20, "OK", Family.NAV),
    HOME(21, "Home", Family.NAV),
    MENU(22, "Menu", Family.NAV),
    BACK(23, "Back", Family.NAV),
    VOLUME_UP(24, "Volume up", Family.VOLUME),
    VOLUME_DOWN(25, "Volume down", Family.VOLUME),
    MUTE(26, "Mute", Family.VOLUME),
    CHANNEL_UP(27, "Channel up", Family.CHANNEL),
    CHANNEL_DOWN(28, "Channel down", Family.CHANNEL),
    BLUE(29, "Blue, teletext", Family.COLOR),
    GREEN(30, "Green, teletext", Family.COLOR),
    RED(31, "Red, teletext", Family.COLOR),
    YELLOW(32, "Yellow, teletext", Family.COLOR),
    PLAY(33, "Play", Family.TRANSPORT),
    PAUSE(34, "Pause", Family.TRANSPORT),
    STOP(35, "Stop", Family.TRANSPORT),
    FAST_FORWARD(36, "Fast forward", Family.TRANSPORT),
    REWIND(37, "Rewind", Family.TRANSPORT),
    SKIP_FORWARD(38, "Skip forward", Family.TRANSPORT),
    SKIP_BACK(39, "Skip backward", Family.TRANSPORT),
    RECORD(40, "Record", Family.TRANSPORT),
    RECORDING_LIST(41, "Recording list", Family.TRANSPORT),
    REPEAT(42, "Repeat", Family.TRANSPORT),
    LIVE_TV(43, "Live TV", Family.NAV),
    EPG(44, "Guide", Family.INFO),
    INFO(45, "Program info", Family.INFO),
    ASPECT(46, "Aspect ratio", Family.PICTURE),
    INPUT(47, "External input", Family.INPUT),
    PIP(48, "PIP secondary video", Family.PIP),
    SUBTITLE(49, "Subtitles", Family.INFO),
    PROGRAM_LIST(50, "Program list", Family.INFO),
    TELETEXT(51, "Teletext", Family.INFO),
    MARK(52, "Mark", Family.INFO),
    THREE_D(400, "3D video", Family.PICTURE),
    THREE_D_LR(401, "3D left-right", Family.PICTURE),
    DASH(402, "Dash", Family.DIGIT),
    FLASHBACK(403, "Previous channel", Family.CHANNEL),
    FAVORITE(404, "Favorite channel", Family.CHANNEL),
    QUICK_MENU(405, "Quick menu", Family.PICTURE),
    TEXT_OPTION(406, "Text option", Family.INFO),
    AUDIO_DESCRIPTION(407, "Audio description", Family.INFO),
    NETCAST(408, "NetCast", Family.NAV),
    ENERGY(409, "Energy saving", Family.PICTURE),
    AV_MODE(410, "A/V mode", Family.PICTURE),
    SIMPLINK(411, "SIMPLINK", Family.INPUT),
    EXIT(412, "Exit", Family.NAV),
    RESERVATIONS(413, "Reservation list", Family.INFO),
    PIP_UP(414, "PIP channel up", Family.PIP),
    PIP_DOWN(415, "PIP channel down", Family.PIP),
    PIP_SWAP(416, "Swap PIP", Family.PIP),
    MY_APPS(417, "My Apps", Family.NAV),
    ;

    /** Held keys that step. Toggles such as Mute, Back, or Previous channel fire once per press. */
    val repeats: Boolean
        get() = this in repeating

    enum class Family { POWER, DIGIT, NAV, VOLUME, CHANNEL, COLOR, TRANSPORT, PICTURE, PIP, INPUT, INFO }

    companion object {
        private val repeating = setOf(UP, DOWN, LEFT, RIGHT, VOLUME_UP, VOLUME_DOWN, CHANNEL_UP, CHANNEL_DOWN)
        val colorOrder = listOf(RED, GREEN, YELLOW, BLUE)
        val digits = listOf(NUM_1, NUM_2, NUM_3, NUM_4, NUM_5, NUM_6, NUM_7, NUM_8, NUM_9, DASH, NUM_0, FLASHBACK)

        fun fromCode(code: Int): KeyCode? = entries.firstOrNull { it.code == code }

        fun fromName(name: String): KeyCode? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
