# WallScreen VNC (Spigot 1.12.2)

Плагин для Minecraft, позволяющий отображать удалённый рабочий стол (через VNC) на экране из рамок и управлять им прямо из игры.

## Команды:
 - /wallscreen create <name> <widthTiles> <heightTiles> [host] [port] [password|-] [widthPx] [heightPx] [fps] [bitrateKbps]
 Пример: /wallscreen create pc3 2 2 192.168.0.11 5901 - 1024 768 5 1200

 - /wallscreen setvnc <name> <host> <port> [password|-]
 - /wallscreen setres <name> <widthPx> <heightPx>
 - /wallscreen setfps <name> <fps>
 - /wallscreen setbitrate <name> <kbps>
 - /wallscreen reconnect <name>
 - /wallscreen demo <name> off/on
 - /wallscreen remove <name>

 - /wallscreen bind <screen> <action>
 - /wallscreen unbind <screen>

Actions:

Мышь:

mouse-left
mouse-right
mouse-up
mouse-down
lmb
rmb

Клавиатура:

key:A
key:ENTER
key:SPACE
key:ESC
key:TAB
key:SHIFT
key:CTRL
key:ALT
key:UP
key:DOWN
key:LEFT
key:RIGHT