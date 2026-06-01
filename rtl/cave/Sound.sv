// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

// Sound PCB integration: sound CPU, sample chips, banking, ROM arbitration, and mixing.
module Sound(
  input         clock,
  input         reset,
  input         io_ctrl_oki_0_wr,
  input  [15:0] io_ctrl_oki_0_din,
  output [15:0] io_ctrl_oki_0_dout,
  input         io_ctrl_oki_1_wr,
  input  [15:0] io_ctrl_oki_1_din,
  output [15:0] io_ctrl_oki_1_dout,
  input         io_ctrl_nmk_wr,
  input  [22:0] io_ctrl_nmk_addr,
  input  [15:0] io_ctrl_nmk_din,
  input         io_ctrl_ymz_rd,
  input         io_ctrl_ymz_wr,
  input  [22:0] io_ctrl_ymz_addr,
  input  [15:0] io_ctrl_ymz_din,
  output [15:0] io_ctrl_ymz_dout,
  input         io_ctrl_req,
  input  [15:0] io_ctrl_data,
  input         io_ctrl_reply_rd,
  output [15:0] io_ctrl_reply,
  output        io_ctrl_irq,
  input  [3:0]  io_gameIndex,
  input  [1:0]  io_gameConfig_sound_0_device,
  output        io_rom_0_rd,
  output [24:0] io_rom_0_addr,
  input  [7:0]  io_rom_0_dout,
  input         io_rom_0_wait_n,
  input         io_rom_0_valid,
  output [24:0] io_rom_1_addr,
  input  [7:0]  io_rom_1_dout,
  input         io_rom_1_valid,
  output [63:0] io_debug,
  output [15:0] io_audio
);
  wire        hotdogZ80;
  wire        mazingerZ80;
  wire        z80Game;
  wire        donpachi;
  wire        soundDeviceIsOki;
  wire        soundDeviceIsYmz;
  wire        soundDeviceIsZ80;

  CaveBoardProfile boardProfile(
    .game_index                  (io_gameIndex),
    .sound_device                (io_gameConfig_sound_0_device),
    .game_is_dfeveron            (),
    .game_is_dodonpachi          (),
    .game_is_donpachi            (donpachi),
    .game_is_esprade             (),
    .game_is_uopoko              (),
    .game_is_guwange             (),
    .game_is_gaia                (),
    .game_is_hotdogstorm         (hotdogZ80),
    .game_is_mazinger            (mazingerZ80),
    .board_uses_z80_sound        (z80Game),
    .board_is_vertical_clockwise (),
    .sound_is_ymz280b            (soundDeviceIsYmz),
    .sound_is_oki                (soundDeviceIsOki),
    .sound_is_z80                (soundDeviceIsZ80)
  );

  reg         reqReg;
  reg  [15:0] dataReg;
  reg  [3:0]  z80BankReg;
  reg  [3:0]  okiBankHiReg;
  reg  [3:0]  okiBankLoReg;
  reg  [15:0] ymzAudioReg;
  reg  [15:0] ym2203PsgAudioReg;
  reg  [15:0] ym2203FmAudioReg;
  reg  [13:0] oki0AudioReg;
  reg  [13:0] oki1AudioReg;
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
  reg  [7:0]  debugLastOkiPhrase;
  reg  [7:0]  debugLastOkiStart;
  reg  [7:0]  debugLastOkiBank;
  reg  [7:0]  debugLastSoundCommand;
  reg  [7:0]  debugLastReply;
  reg  [7:0]  debugLastYmReg;
  reg  [7:0]  debugLastYmData;
  reg  [7:0]  debugFlags;
  reg         debugOkiPending;
`endif
  reg         z80IoWrD;
  reg  [7:0]  replyFifo [0:31];
  reg  [4:0]  replyReadPtr;
  reg  [4:0]  replyWritePtr;
  reg  [5:0]  replyCount;

  wire [15:0] cpuAddr;
  wire [7:0]  cpuDout;
  wire        cpuRd;
  wire        cpuWr;
  wire        cpuRfsh;
  wire        cpuMreq;
  wire        cpuIorq;
  wire        cpuInt;

  wire        z80ProgRomSelect = cpuAddr < 16'h4000;
  wire        z80BankRomSelect = (|cpuAddr[15:14]) & ~cpuAddr[15];
  wire        z80RamSelect =
    hotdogZ80 ? cpuAddr > 16'hDFFF :
    mazingerZ80 ? ((cpuAddr > 16'hBFFF) & (cpuAddr < 16'hC800)) | (cpuAddr > 16'hF7FF) :
                  1'b0;
  wire [15:0] cpuIoAddr = {8'h00, cpuAddr[7:0]};
  wire        z80IoWr = z80Game & cpuIorq & cpuWr;
  wire        z80IoWrPulse = z80IoWr & ~z80IoWrD;
  wire        latchLowRead = z80Game & (cpuIoAddr == 16'h0030) & cpuIorq & cpuRd;
  wire        latchHighRead = hotdogZ80 & (cpuIoAddr == 16'h0040) & cpuIorq & cpuRd;
  wire        ym2203Write = z80Game & (cpuIoAddr > 16'h004F) & (cpuIoAddr < 16'h0052) & z80IoWrPulse;
  wire        ym2203Read =
    ((hotdogZ80 & (cpuIoAddr > 16'h004F) & (cpuIoAddr < 16'h0052)) |
     (mazingerZ80 & (cpuIoAddr > 16'h0051) & (cpuIoAddr < 16'h0054))) & cpuIorq & cpuRd;
  wire        oki1Access =
    ((hotdogZ80 & (cpuIoAddr == 16'h0060)) |
     (mazingerZ80 & (cpuIoAddr == 16'h0070))) & cpuIorq;
  wire        hotdogOkiBankWrite = hotdogZ80 & (cpuIoAddr == 16'h0070) & z80IoWrPulse;
  wire        mazingerOkiBankWrite = mazingerZ80 & (cpuIoAddr == 16'h0074) & z80IoWrPulse;
  wire        okiBankWrite = hotdogOkiBankWrite | mazingerOkiBankWrite;
  wire        soundReplyWrite = mazingerZ80 & (cpuIoAddr == 16'h0010) & z80IoWrPulse;
  wire        replyPop = io_ctrl_reply_rd & (replyCount != 6'd0);
  wire        replyPush = soundReplyWrite & ((replyCount != 6'd32) | replyPop);

  wire [7:0]  soundRamDout;
  wire        soundRamRd = z80Game & z80RamSelect & ~cpuRfsh;
  wire        soundRamWr = z80Game & z80RamSelect & cpuMreq & cpuWr;

  wire [7:0]  progRomDout;
  wire [7:0]  bankRomDout;
  wire [7:0]  romOrBankDout = z80BankRomSelect & cpuMreq & cpuRd ? bankRomDout : progRomDout;

  wire [7:0]  oki0CpuDout;
  wire [17:0] oki0RomAddr;
  wire [7:0]  oki0RomDout;
  wire        oki0RomValid;
  wire        oki0AudioValid;
  wire [13:0] oki0Audio;

  wire [7:0]  oki1CpuDout;
  wire [17:0] oki1RomAddr;
  wire        oki1AudioValid;
  wire [13:0] oki1Audio;
  wire        z80Oki1CpuWr = oki1Access & z80IoWrPulse;
  wire        oki1CpuWrRaw = z80Game ? z80Oki1CpuWr : io_ctrl_oki_1_wr;
  wire [7:0]  oki1CpuDinRaw = z80Game ? cpuDout : io_ctrl_oki_1_din[7:0];
  wire        oki1CpuWr = oki1CpuWrRaw;
  wire [7:0]  oki1CpuDin = oki1CpuDinRaw;
  wire [16:0] oki1CenStep = mazingerZ80 ? 17'h0873 : 17'h10E5;

  wire [7:0]  ym2203CpuDout;
  wire        ym2203AudioValid;
  wire [15:0] ym2203PsgAudio;
  wire [15:0] ym2203FmAudio;
  wire [7:0]  cpuDin =
    oki1Access & cpuRd    ? oki1CpuDout :
    ym2203Read            ? ym2203CpuDout :
    latchHighRead         ? dataReg[15:8] :
    latchLowRead          ? dataReg[7:0] :
    z80RamSelect & cpuMreq & cpuRd ? soundRamDout :
    romOrBankDout;

  wire [7:0]  ymzCpuDout;
  wire        ymzRomRd;
  wire [23:0] ymzRomAddr;
  wire [7:0]  ymzRomDout;
  wire        ymzRomWaitN;
  wire        ymzRomValid;
  wire        ymzAudioValid;
  wire [15:0] ymzAudio;

  wire [24:0] nmkOki0AddrOut;
  wire [24:0] nmkOki1AddrOut;
  wire [3:0]  oki0Bank = oki0RomAddr[17] ? okiBankHiReg : okiBankLoReg;
  wire [3:0]  oki1Bank = oki1RomAddr[17] ? okiBankHiReg : okiBankLoReg;
  wire [24:0] oki0MappedAddr = donpachi ? nmkOki0AddrOut : {4'h0, oki0Bank, oki0RomAddr[16:0]};
  wire [24:0] oki1MappedAddr = donpachi ? nmkOki1AddrOut : {4'h0, oki1Bank, oki1RomAddr[16:0]};

  always @(posedge clock) begin
    if (reset) begin
      reqReg <= 1'b0;
      z80BankReg <= 4'h0;
      okiBankHiReg <= 4'h0;
      okiBankLoReg <= 4'h0;
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
      debugLastOkiPhrase <= 8'h0;
      debugLastOkiStart <= 8'h0;
      debugLastOkiBank <= 8'h0;
      debugLastSoundCommand <= 8'h0;
      debugLastReply <= 8'h0;
      debugLastYmReg <= 8'h0;
      debugLastYmData <= 8'h0;
      debugFlags <= 8'h0;
      debugOkiPending <= 1'b0;
`endif
      z80IoWrD <= 1'b0;
      replyReadPtr <= 5'd0;
      replyWritePtr <= 5'd0;
      replyCount <= 6'd0;
    end
    else begin
      z80IoWrD <= z80IoWr;

      reqReg <= ~(z80Game & (latchHighRead | latchLowRead)) & (io_ctrl_req | reqReg);
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
      debugFlags[0] <= debugOkiPending;
      debugFlags[1] <= reqReg;
      debugFlags[2] <= cpuInt;
      debugFlags[3] <= z80IoWrPulse;
      debugFlags[4] <= io_rom_1_valid;
      debugFlags[5] <= oki1AudioValid;
      debugFlags[6] <= oki1CpuWrRaw;
      debugFlags[7] <= okiBankWrite;

      if (io_ctrl_req)
        debugLastSoundCommand <= io_ctrl_data[7:0];
`endif

      if (z80Game & (cpuIoAddr == 16'h0000) & z80IoWrPulse)
        z80BankReg <= mazingerZ80 ? {1'b0, cpuDout[2:0]} : cpuDout[3:0];

      if (okiBankWrite) begin
        okiBankHiReg <= {2'b00, cpuDout[5:4]};
        okiBankLoReg <= {2'b00, cpuDout[1:0]};
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
        debugLastOkiBank <= cpuDout;
`endif
      end

`ifdef CAVE_ENABLE_DEBUG_OVERLAY
      if (ym2203Write) begin
        if (cpuAddr[0])
          debugLastYmData <= cpuDout;
        else
          debugLastYmReg <= cpuDout;
      end

      if (oki1CpuWrRaw) begin
        if (debugOkiPending) begin
          debugLastOkiStart <= oki1CpuDinRaw;
          debugOkiPending <= 1'b0;
        end
        else if (oki1CpuDinRaw[7]) begin
          debugLastOkiPhrase <= {1'b0, oki1CpuDinRaw[6:0]};
          debugOkiPending <= 1'b1;
        end
        else begin
          debugOkiPending <= 1'b0;
        end
      end
`endif

      if (replyPush) begin
        replyFifo[replyWritePtr] <= cpuDout;
        replyWritePtr <= replyWritePtr + 5'd1;
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
        debugLastReply <= cpuDout;
`endif
      end

      if (replyPop)
        replyReadPtr <= replyReadPtr + 5'd1;

      case ({replyPush, replyPop})
        2'b10: replyCount <= replyCount + 6'd1;
        2'b01: replyCount <= replyCount - 6'd1;
        default: begin
        end
      endcase
    end

    if (io_ctrl_req)
      dataReg <= io_ctrl_data;

    if (ymzAudioValid)
      ymzAudioReg <= ymzAudio;

    if (ym2203AudioValid) begin
      ym2203PsgAudioReg <= ym2203PsgAudio;
      ym2203FmAudioReg <= ym2203FmAudio;
    end

    if (oki0AudioValid)
      oki0AudioReg <= oki0Audio;

    if (oki1AudioValid)
      oki1AudioReg <= oki1Audio;
  end

  CaveSoundZ80Cpu cpu (
    .clock   (clock),
    .reset   (reset),
    .io_addr (cpuAddr),
    .io_din  (cpuDin),
    .io_dout (cpuDout),
    .io_rd   (cpuRd),
    .io_wr   (cpuWr),
    .io_rfsh (cpuRfsh),
    .io_mreq (cpuMreq),
    .io_iorq (cpuIorq),
    .io_int  (cpuInt),
    .io_nmi  (reqReg)
  );

  CaveSinglePortRam #(
    .ADDR_WIDTH  (13),
    .DATA_WIDTH  (8),
    .DEPTH       (0),
    .MASK_ENABLE (0)
  ) soundRam (
    .clock (clock),
    .rd    (soundRamRd),
    .wr    (soundRamWr),
    .addr  (cpuAddr[12:0]),
    .mask  (1'b0),
    .din   (cpuDout),
    .dout  (soundRamDout)
  );

  NMK112 nmk (
    .clock         (clock),
    .io_cpu_wr     (io_ctrl_nmk_wr),
    .io_cpu_addr   (io_ctrl_nmk_addr),
    .io_cpu_din    (io_ctrl_nmk_din),
    .io_addr_0_in  ({7'h00, oki0RomAddr}),
    .io_addr_0_out (nmkOki0AddrOut),
    .io_addr_1_in  ({7'h00, oki1RomAddr}),
    .io_addr_1_out (nmkOki1AddrOut)
  );

  CaveOKIM6295 oki_0 (
    .clock           (clock),
    .reset           (reset),
    .io_cen_step     (17'h0873),
    .io_cpu_wr       (io_ctrl_oki_0_wr),
    .io_cpu_din      (io_ctrl_oki_0_din[7:0]),
    .io_wait_for_rom (1'b0),
    .io_cpu_dout     (oki0CpuDout),
    .io_rom_addr     (oki0RomAddr),
    .io_rom_dout     (oki0RomDout),
    .io_rom_valid    (oki0RomValid),
    .io_audio_valid  (oki0AudioValid),
    .io_audio_bits   (oki0Audio)
  );

  CaveOKIM6295 #(
    .INTERPOL (2)
  ) oki_1 (
    .clock           (clock),
    .reset           (reset),
    .io_cen_step     (oki1CenStep),
    .io_cpu_wr       (oki1CpuWr),
    .io_cpu_din      (oki1CpuDin),
    .io_wait_for_rom (mazingerZ80),
    .io_cpu_dout     (oki1CpuDout),
    .io_rom_addr     (oki1RomAddr),
    .io_rom_dout     (io_rom_1_dout),
    .io_rom_valid    (io_rom_1_valid),
    .io_audio_valid  (oki1AudioValid),
    .io_audio_bits   (oki1Audio)
  );

  YMZ280B ymz280b (
    .clock              (clock),
    .reset              (reset),
    .io_cpu_rd          (io_ctrl_ymz_rd),
    .io_cpu_wr          (io_ctrl_ymz_wr),
    .io_cpu_addr        (io_ctrl_ymz_addr[0]),
    .io_cpu_din         (io_ctrl_ymz_din[7:0]),
    .io_cpu_dout        (ymzCpuDout),
    .io_rom_rd          (ymzRomRd),
    .io_rom_addr        (ymzRomAddr),
    .io_rom_dout        (ymzRomDout),
    .io_rom_wait_n      (ymzRomWaitN),
    .io_rom_valid       (ymzRomValid),
    .io_audio_valid     (ymzAudioValid),
    .io_audio_bits_left (ymzAudio),
    .io_irq             (io_ctrl_irq)
  );

  YM2203 ym2203 (
    .clock             (clock),
    .reset             (reset),
    .io_cpu_wr         (ym2203Write),
    .io_cpu_addr       (cpuAddr[0]),
    .io_cpu_din        (cpuDout),
    .io_cpu_dout       (ym2203CpuDout),
    .io_irq            (cpuInt),
    .io_audio_valid    (ym2203AudioValid),
    .io_audio_bits_psg (ym2203PsgAudio),
    .io_audio_bits_fm  (ym2203FmAudio)
  );

  CaveSoundRomReadArbiter arbiter (
    .clock          (clock),
    .reset          (reset),
    .io_in_0_rd     (soundDeviceIsOki),
    .io_in_0_addr   (oki0MappedAddr),
    .io_in_0_dout   (oki0RomDout),
    .io_in_0_valid  (oki0RomValid),
    .io_in_1_rd     (soundDeviceIsYmz & ymzRomRd),
    .io_in_1_addr   ({1'b0, ymzRomAddr}),
    .io_in_1_dout   (ymzRomDout),
    .io_in_1_wait_n (ymzRomWaitN),
    .io_in_1_valid  (ymzRomValid),
    .io_in_2_rd     (soundDeviceIsZ80 & z80Game & z80ProgRomSelect & ~cpuRfsh),
    .io_in_2_addr   ({9'h000, cpuAddr}),
    .io_in_2_dout   (progRomDout),
    .io_in_3_rd     (soundDeviceIsZ80 & z80Game & z80BankRomSelect & ~cpuRfsh),
    .io_in_3_addr   ({7'h00, z80BankReg, cpuAddr[13:0]}),
    .io_in_3_dout   (bankRomDout),
    .io_out_rd      (io_rom_0_rd),
    .io_out_addr    (io_rom_0_addr),
    .io_out_dout    (io_rom_0_dout),
    .io_out_wait_n  (io_rom_0_wait_n),
    .io_out_valid   (io_rom_0_valid)
  );

  AudioMixer io_audio_mixer (
    .clock   (clock),
    .io_mazinger (mazingerZ80),
    .io_in_4 (oki1AudioReg),
    .io_in_3 (oki0AudioReg),
    .io_in_2 (ym2203FmAudioReg),
    .io_in_1 (ym2203PsgAudioReg),
    .io_in_0 (ymzAudioReg),
    .io_out  (io_audio)
  );

  assign io_ctrl_oki_0_dout = {8'h00, oki0CpuDout};
  assign io_ctrl_oki_1_dout = {8'h00, oki1CpuDout};
  assign io_ctrl_ymz_dout = {8'h00, ymzCpuDout};
  assign io_ctrl_reply = (replyCount == 6'd0) ? 16'h00ff : {8'h00, replyFifo[replyReadPtr]};
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
  wire [63:0] debugCommandBits = {
    debugFlags,
    debugLastYmData,
    debugLastYmReg,
    debugLastOkiStart,
    debugLastOkiPhrase,
    debugLastOkiBank,
    debugLastReply,
    debugLastSoundCommand
  };

  assign io_debug = debugCommandBits;
`else
  assign io_debug = 64'd0;
`endif
  assign io_rom_1_addr = oki1MappedAddr;
endmodule
