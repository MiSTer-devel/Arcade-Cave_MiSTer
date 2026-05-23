module CaveBoardProfile(
  input  [3:0] game_index,
  input  [1:0] sound_device,
  output       game_is_dfeveron,
  output       game_is_dodonpachi,
  output       game_is_donpachi,
  output       game_is_esprade,
  output       game_is_uopoko,
  output       game_is_guwange,
  output       game_is_gaia,
  output       game_is_hotdogstorm,
  output       game_is_mazinger,
  output       board_uses_z80_sound,
  output       board_is_vertical_clockwise,
  output       sound_is_ymz280b,
  output       sound_is_oki,
  output       sound_is_z80
);
  localparam [3:0] GAME_DFEVERON = 4'h0;
  localparam [3:0] GAME_DDONPACH = 4'h1;
  localparam [3:0] GAME_DONPACHI = 4'h2;
  localparam [3:0] GAME_ESPRADE  = 4'h3;
  localparam [3:0] GAME_UOPOKO   = 4'h4;
  localparam [3:0] GAME_GUWANGE  = 4'h5;
  localparam [3:0] GAME_GAIA     = 4'h6;
  localparam [3:0] GAME_HOTDOGST = 4'h7;
  localparam [3:0] GAME_MAZINGER = 4'h8;

  localparam [1:0] SOUND_DEVICE_YMZ280B  = 2'h1;
  localparam [1:0] SOUND_DEVICE_OKIM6259 = 2'h2;
  localparam [1:0] SOUND_DEVICE_Z80      = 2'h3;

  assign game_is_dfeveron = game_index == GAME_DFEVERON;
  assign game_is_dodonpachi = game_index == GAME_DDONPACH;
  assign game_is_donpachi = game_index == GAME_DONPACHI;
  assign game_is_esprade = game_index == GAME_ESPRADE;
  assign game_is_uopoko = game_index == GAME_UOPOKO;
  assign game_is_guwange = game_index == GAME_GUWANGE;
  assign game_is_gaia = game_index == GAME_GAIA;
  assign game_is_hotdogstorm = game_index == GAME_HOTDOGST;
  assign game_is_mazinger = game_index == GAME_MAZINGER;

  assign board_uses_z80_sound = game_is_hotdogstorm | game_is_mazinger;
  assign board_is_vertical_clockwise = game_is_hotdogstorm | game_is_mazinger;

  assign sound_is_ymz280b = sound_device == SOUND_DEVICE_YMZ280B;
  assign sound_is_oki = sound_device == SOUND_DEVICE_OKIM6259;
  assign sound_is_z80 = sound_device == SOUND_DEVICE_Z80;
endmodule
