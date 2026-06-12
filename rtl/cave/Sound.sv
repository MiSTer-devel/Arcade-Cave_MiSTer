// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

// Atlus-class sound integration: YMZ280B, DonPachi dual OKI/NMK112, and
// Power Instinct 2 Z80/YM2203/dual OKI/NMK112.
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
  output        io_ctrl_reply_empty,
  output        io_ctrl_irq,
  input  [3:0]  io_gameIndex,
  input  [1:0]  io_gameConfig_sound_0_device,
  input         io_options_ym_psg,
  input         io_options_ym_fm,
  input         io_options_oki_0,
  input         io_options_oki_1,
  output        io_rom_0_rd,
  output [24:0] io_rom_0_addr,
  input  [7:0]  io_rom_0_dout,
  input         io_rom_0_wait_n,
  input         io_rom_0_valid,
  output        io_rom_1_rd,
  output [24:0] io_rom_1_addr,
  input  [7:0]  io_rom_1_dout,
  input         io_rom_1_valid,
  output        io_rom_2_rd,
  output [24:0] io_rom_2_addr,
  input  [7:0]  io_rom_2_dout,
  input         io_rom_2_valid,
  output [63:0] io_debug,
  output [15:0] io_audio
);
  wire        donpachi;
  wire        pwrinst2;
  wire        soundDeviceIsOki;
  wire        soundDeviceIsYmz;
  wire        soundDeviceIsZ80;
  wire        pwrinst2Z80Sound;

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
    .game_is_pwrinst2            (pwrinst2),
    .board_uses_z80_sound        (),
    .board_is_vertical_clockwise (),
    .sound_is_ymz280b            (soundDeviceIsYmz),
    .sound_is_oki                (soundDeviceIsOki),
    .sound_is_z80                (soundDeviceIsZ80)
  );

  assign pwrinst2Z80Sound = pwrinst2 & soundDeviceIsZ80;

  reg         reqReg;
  reg  [15:0] dataReg;
  reg  [2:0]  z80BankReg;
  reg         z80IoWrD;
  reg  [7:0]  z80IoWrAddrD;
  reg  [7:0]  z80IoWrDataD;
  reg  [7:0]  replyFifo [0:31];
  reg  [4:0]  replyReadPtr;
  reg  [4:0]  replyWritePtr;
  reg  [5:0]  replyCount;

  reg  [3:0]  okiBankHiReg;
  reg  [3:0]  okiBankLoReg;
  reg  [15:0] ymzAudioReg;
  reg  [15:0] ym2203PsgAudioReg;
  reg  [15:0] ym2203FmAudioReg;
  reg  [13:0] oki0AudioReg;
  reg  [13:0] oki1AudioReg;

`ifdef CAVE_ENABLE_DEBUG_OVERLAY
  reg  [7:0]  debugLastSoundCommand;
  reg  [7:0]  debugOki0PhraseCommand;
  reg  [7:0]  debugOki0StartCommand;
  reg  [7:0]  debugOki0StatusRead;
  reg  [7:0]  debugOki0StartChannels;
  reg  [7:0]  debugOki0BusyOverlap;
  reg  [7:0]  debugOki1StatusRead;
  reg  [7:0]  debugLastNmkWrite;
  reg  [7:0]  debugLastYmReg;
  reg  [7:0]  debugLastYmData;
  reg  [7:0]  debugYmFlags;
  reg  [7:0]  debugYmPsgPeak;
  reg  [7:0]  debugYmFmPeak;
  reg         debugOki0Pending;
`endif

  wire [15:0] cpuAddr;
  wire [7:0]  cpuDout;
  wire        cpuRd;
  wire        cpuWr;
  wire        cpuRfsh;
  wire        cpuMreq;
  wire        cpuIorq;
  wire        cpuInt;
  wire [15:0] cpuIoAddr = {8'h00, cpuAddr[7:0]};

  wire        z80ProgRomSelect = cpuAddr < 16'h8000;
  wire        z80BankRomSelect = (cpuAddr >= 16'h8000) & (cpuAddr < 16'hC000);
  wire        z80RamSelect = cpuAddr >= 16'hE000;
  wire        z80IoWr = pwrinst2Z80Sound & cpuIorq & cpuWr;
  wire        z80IoWrChanged =
    z80IoWr & ((cpuIoAddr[7:0] != z80IoWrAddrD) | (cpuDout != z80IoWrDataD));
  wire        z80IoWrPulse = z80IoWr & (~z80IoWrD | z80IoWrChanged);

  wire        pwrinst2Oki0Access = pwrinst2Z80Sound & (cpuIoAddr == 16'h0000) & cpuIorq;
  wire        pwrinst2Oki1Access = pwrinst2Z80Sound & (cpuIoAddr == 16'h0008) & cpuIorq;
  wire        pwrinst2NmkWrite =
    pwrinst2Z80Sound & (cpuIoAddr >= 16'h0010) & (cpuIoAddr < 16'h0018) & z80IoWrPulse;
  wire        ym2203Access =
    pwrinst2Z80Sound & (cpuIoAddr >= 16'h0040) & (cpuIoAddr < 16'h0042) & cpuIorq;
  wire        ym2203Write = ym2203Access & z80IoWrPulse;
  wire        ym2203Read = ym2203Access & cpuRd;
  wire        soundReplyWrite = pwrinst2Z80Sound & (cpuIoAddr == 16'h0050) & z80IoWrPulse;
  wire        latchHighRead = pwrinst2Z80Sound & (cpuIoAddr == 16'h0060) & cpuIorq & cpuRd;
  wire        latchLowRead = pwrinst2Z80Sound & (cpuIoAddr == 16'h0070) & cpuIorq & cpuRd;
  wire        z80BankWrite = pwrinst2Z80Sound & (cpuIoAddr == 16'h0080) & z80IoWrPulse;
  wire        replyPop = io_ctrl_reply_rd & (replyCount != 6'd0);
  wire        replyPush = soundReplyWrite & ((replyCount != 6'd32) | replyPop);

  wire [7:0]  soundRamDout;
  wire        soundRamRd = pwrinst2Z80Sound & z80RamSelect & cpuMreq & cpuRd & ~cpuRfsh;
  wire        soundRamWr = pwrinst2Z80Sound & z80RamSelect & cpuMreq & cpuWr;

  wire [7:0]  progRomDout;
  wire        progRomValid;
  wire [7:0]  bankRomDout;
  wire        bankRomValid;
  wire        z80RomRead =
    pwrinst2Z80Sound & cpuMreq & cpuRd & ~cpuRfsh & (z80ProgRomSelect | z80BankRomSelect);
  wire        z80ProgRomArbiterRead =
    pwrinst2Z80Sound & z80ProgRomSelect & cpuMreq & cpuRd & ~cpuRfsh;
  wire        z80BankRomArbiterRead =
    pwrinst2Z80Sound & z80BankRomSelect & cpuMreq & cpuRd & ~cpuRfsh;
  wire        z80RomValid = z80BankRomSelect ? bankRomValid : progRomValid;
  wire        z80WaitN = ~z80RomRead | z80RomValid;
  wire [7:0]  romOrBankDout =
    z80BankRomSelect & cpuMreq & cpuRd ? bankRomDout : progRomDout;

  wire [7:0]  oki0CpuDout;
  wire        oki0RomRead;
  wire [17:0] oki0RomAddr;
  wire [7:0]  oki0RomDout;
  wire        oki0RomValid;
  wire        oki0AudioValid;
  wire [13:0] oki0Audio;
  wire [47:0] oki0DebugCtrlBytes;
  wire [47:0] oki0DebugBodyBytes;

  wire [7:0]  oki1CpuDout;
  wire        oki1RomRead;
  wire [17:0] oki1RomAddr;
  wire        oki1AudioValid;
  wire [13:0] oki1Audio;
  wire [47:0] oki1DebugCtrlBytes;
  wire [47:0] oki1DebugBodyBytes;

  wire [7:0]  ymzCpuDout;
  wire        ymzRomRd;
  wire [23:0] ymzRomAddr;
  wire [7:0]  ymzRomDout;
  wire        ymzRomWaitN;
  wire        ymzRomValid;
  wire        ymzAudioValid;
  wire [15:0] ymzAudio;
  wire        ymzIrq;

  wire [7:0]  ym2203CpuDout;
  wire        ym2203Irq;
  wire        ym2203AudioValid;
  wire [15:0] ym2203PsgAudio;
  wire [15:0] ym2203FmAudio;
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
  wire [7:0]  ym2203PsgMagnitude = ym2203PsgAudio[15:8];
  wire [7:0]  ym2203FmMagnitude = ym2203FmAudio[15] ? ~ym2203FmAudio[14:7] : ym2203FmAudio[14:7];
`endif

  wire        nmkWr = pwrinst2NmkWrite | io_ctrl_nmk_wr;
  wire [22:0] nmkAddr = pwrinst2NmkWrite ? {15'h0000, cpuIoAddr[7:0]} : io_ctrl_nmk_addr;
  wire [15:0] nmkDin = pwrinst2NmkWrite ? {8'h00, cpuDout} : io_ctrl_nmk_din;
  wire [24:0] nmkOki0AddrOut;
  wire [24:0] nmkOki1AddrOut;
  wire [3:0]  oki0Bank = oki0RomAddr[17] ? okiBankHiReg : okiBankLoReg;
  wire [3:0]  oki1Bank = oki1RomAddr[17] ? okiBankHiReg : okiBankLoReg;
  wire [24:0] bankedOki0MappedAddr = {4'h0, oki0Bank, oki0RomAddr[16:0]};
  wire [24:0] bankedOki1MappedAddr = {4'h0, oki1Bank, oki1RomAddr[16:0]};
  wire [24:0] oki0MappedAddr = (donpachi | pwrinst2Z80Sound) ? nmkOki0AddrOut : bankedOki0MappedAddr;
  wire [24:0] oki1MappedAddr = (donpachi | pwrinst2Z80Sound) ? nmkOki1AddrOut : bankedOki1MappedAddr;
  wire [7:0]  oki0RomData = pwrinst2Z80Sound ? io_rom_1_dout : oki0RomDout;
  wire        oki0RomDataValid = pwrinst2Z80Sound ? io_rom_1_valid : oki0RomValid;
  wire [7:0]  oki1RomData = pwrinst2Z80Sound ? io_rom_2_dout : io_rom_1_dout;
  wire        oki1RomDataValid = pwrinst2Z80Sound ? io_rom_2_valid : io_rom_1_valid;
  wire [15:0] ym2203PsgMix = io_options_ym_psg ? ym2203PsgAudioReg : 16'h4000;
  wire [15:0] ym2203FmMix = io_options_ym_fm ? ym2203FmAudioReg : 16'h0000;
  wire [13:0] oki0Mix = io_options_oki_0 ? oki0AudioReg : 14'h0000;
  wire [13:0] oki1Mix = io_options_oki_1 ? oki1AudioReg : 14'h0000;

  wire        oki0CpuWr = pwrinst2Z80Sound ? (pwrinst2Oki0Access & z80IoWrPulse) : io_ctrl_oki_0_wr;
  wire [7:0]  oki0CpuDin = pwrinst2Z80Sound ? cpuDout : io_ctrl_oki_0_din[7:0];
  wire        oki1CpuWr = pwrinst2Z80Sound ? (pwrinst2Oki1Access & z80IoWrPulse) : io_ctrl_oki_1_wr;
  wire [7:0]  oki1CpuDin = pwrinst2Z80Sound ? cpuDout : io_ctrl_oki_1_din[7:0];
  wire [16:0] oki0CenStep = pwrinst2Z80Sound ? 17'h1800 : 17'h0873;
  wire [16:0] oki1CenStep = pwrinst2Z80Sound ? 17'h1800 : 17'h10E5;
  wire        okiPin7Ss = ~pwrinst2Z80Sound;

  wire [7:0] cpuDin =
    pwrinst2Oki0Access & cpuRd ? oki0CpuDout :
    pwrinst2Oki1Access & cpuRd ? oki1CpuDout :
    ym2203Read ? ym2203CpuDout :
    latchHighRead ? dataReg[15:8] :
    latchLowRead ? dataReg[7:0] :
    z80RamSelect & cpuMreq & cpuRd ? soundRamDout :
    romOrBankDout;

  always @(posedge clock) begin
    if (reset) begin
      reqReg <= 1'b0;
      dataReg <= 16'h0000;
      z80BankReg <= 3'h0;
      okiBankHiReg <= 4'h0;
      okiBankLoReg <= 4'h0;
      z80IoWrD <= 1'b0;
      z80IoWrAddrD <= 8'h00;
      z80IoWrDataD <= 8'h00;
      replyReadPtr <= 5'd0;
      replyWritePtr <= 5'd0;
      replyCount <= 6'd0;
`ifdef CAVE_ENABLE_DEBUG_OVERLAY
      debugLastSoundCommand <= 8'h0;
      debugOki0PhraseCommand <= 8'h0;
      debugOki0StartCommand <= 8'h0;
      debugOki0StatusRead <= 8'h0;
      debugOki0StartChannels <= 8'h0;
      debugOki0BusyOverlap <= 8'h0;
      debugOki1StatusRead <= 8'h0;
      debugLastNmkWrite <= 8'h0;
      debugLastYmReg <= 8'h0;
      debugLastYmData <= 8'h0;
      debugYmFlags <= 8'h0;
      debugYmPsgPeak <= 8'h0;
      debugYmFmPeak <= 8'h0;
      debugOki0Pending <= 1'b0;
`endif
    end
    else begin
      z80IoWrD <= z80IoWr;
      if (z80IoWr) begin
        z80IoWrAddrD <= cpuIoAddr[7:0];
        z80IoWrDataD <= cpuDout;
      end

      reqReg <= ~(pwrinst2Z80Sound & (latchHighRead | latchLowRead)) & (io_ctrl_req | reqReg);

      if (io_ctrl_req)
        dataReg <= io_ctrl_data;

      if (z80BankWrite)
        z80BankReg <= cpuDout[2:0];

      if (~pwrinst2Z80Sound & io_ctrl_nmk_wr) begin
        okiBankHiReg <= io_ctrl_nmk_din[7:4];
        okiBankLoReg <= io_ctrl_nmk_din[3:0];
      end

      if (replyPush) begin
        replyFifo[replyWritePtr] <= cpuDout;
        replyWritePtr <= replyWritePtr + 5'd1;
      end

      if (replyPop)
        replyReadPtr <= replyReadPtr + 5'd1;

      case ({replyPush, replyPop})
        2'b10: replyCount <= replyCount + 6'd1;
        2'b01: replyCount <= replyCount - 6'd1;
        default: begin
        end
      endcase

`ifdef CAVE_ENABLE_DEBUG_OVERLAY
      if (io_ctrl_req)
        debugLastSoundCommand <= io_ctrl_data[7:0];
      else if (io_ctrl_ymz_wr)
        debugLastSoundCommand <= io_ctrl_ymz_din[7:0];
      else if (io_ctrl_oki_1_wr)
        debugLastSoundCommand <= io_ctrl_oki_1_din[7:0];
      else if (io_ctrl_oki_0_wr)
        debugLastSoundCommand <= io_ctrl_oki_0_din[7:0];

      if (pwrinst2NmkWrite)
        debugLastNmkWrite <= cpuDout;
      else if (~pwrinst2Z80Sound & io_ctrl_nmk_wr)
        debugLastNmkWrite <= io_ctrl_nmk_din[7:0];

      if (pwrinst2Oki0Access & cpuRd)
        debugOki0StatusRead <= oki0CpuDout;

      if (pwrinst2Oki1Access & cpuRd)
        debugOki1StatusRead <= oki1CpuDout;

      debugYmFlags[0] <= pwrinst2Z80Sound;
      debugYmFlags[1] <= io_options_ym_psg;
      debugYmFlags[2] <= io_options_ym_fm;
      debugYmFlags[3] <= ym2203Irq;

      if (ym2203Write) begin
        debugYmFlags[4] <= 1'b1;
        if (cpuAddr[0])
          debugLastYmData <= cpuDout;
        else
          debugLastYmReg <= cpuDout;
      end

      if (ym2203AudioValid) begin
        debugYmFlags[5] <= 1'b1;
        if (ym2203PsgMagnitude > debugYmPsgPeak)
          debugYmPsgPeak <= ym2203PsgMagnitude;
        if (ym2203FmMagnitude > debugYmFmPeak)
          debugYmFmPeak <= ym2203FmMagnitude;
        if (ym2203PsgMagnitude != 8'h00)
          debugYmFlags[6] <= 1'b1;
        if (ym2203FmMagnitude != 8'h00)
          debugYmFlags[7] <= 1'b1;
      end

      if (oki0CpuWr) begin
        if (debugOki0Pending) begin
          debugOki0StartCommand <= oki0CpuDin;
          debugOki0StartChannels <= {4'h0, oki0CpuDin[7:4]};
          debugOki0BusyOverlap <= {4'h0, oki0CpuDout[3:0] & oki0CpuDin[7:4]};
          debugOki0Pending <= 1'b0;
        end
        else if (oki0CpuDin[7]) begin
          debugOki0PhraseCommand <= oki0CpuDin;
          debugOki0Pending <= 1'b1;
        end
        else begin
          debugOki0Pending <= 1'b0;
        end
      end

`endif
    end

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
    .clock         (clock),
    .reset         (reset | ~pwrinst2Z80Sound),
    .io_addr       (cpuAddr),
    .io_din        (cpuDin),
    .io_dout       (cpuDout),
    .io_rd         (cpuRd),
    .io_wr         (cpuWr),
    .io_rfsh       (cpuRfsh),
    .io_mreq       (cpuMreq),
    .io_iorq       (cpuIorq),
    .io_fast_clock (1'b1),
    .io_wait_n     (z80WaitN),
    .io_int        (cpuInt),
    .io_nmi        (reqReg)
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
    .clock           (clock),
    .reset           (reset),
    .io_page_table_0 (pwrinst2Z80Sound),
    .io_page_six_bit (pwrinst2Z80Sound),
    .io_cpu_wr       (nmkWr),
    .io_cpu_addr     (nmkAddr),
    .io_cpu_din      (nmkDin),
    .io_addr_0_in    ({7'h00, oki0RomAddr}),
    .io_addr_0_out   (nmkOki0AddrOut),
    .io_addr_1_in    ({7'h00, oki1RomAddr}),
    .io_addr_1_out   (nmkOki1AddrOut)
  );

  CaveOKIM6295 #(
    .INTERPOL (2)
  ) oki_0 (
    .clock             (clock),
    .reset             (reset),
    .io_cen_step       (oki0CenStep),
    .io_ss             (okiPin7Ss),
    .io_cpu_wr         (oki0CpuWr),
    .io_cpu_din        (oki0CpuDin),
    .io_stretch_cpu_wr (1'b0),
    .io_wait_for_rom   (pwrinst2Z80Sound),
    .io_ignore_busy_start (pwrinst2Z80Sound),
    .io_cpu_dout       (oki0CpuDout),
    .io_rom_rd         (oki0RomRead),
    .io_rom_addr       (oki0RomAddr),
    .io_rom_cache_addr (oki0MappedAddr),
    .io_rom_dout       (oki0RomData),
    .io_rom_valid      (oki0RomDataValid),
    .io_audio_valid    (oki0AudioValid),
    .io_audio_bits     (oki0Audio),
    .io_debug_ctrl_bytes (oki0DebugCtrlBytes),
    .io_debug_body_bytes (oki0DebugBodyBytes)
  );

  CaveOKIM6295 #(
    .INTERPOL (2)
  ) oki_1 (
    .clock             (clock),
    .reset             (reset),
    .io_cen_step       (oki1CenStep),
    .io_ss             (okiPin7Ss),
    .io_cpu_wr         (oki1CpuWr),
    .io_cpu_din        (oki1CpuDin),
    .io_stretch_cpu_wr (1'b0),
    .io_wait_for_rom   (pwrinst2Z80Sound),
    .io_ignore_busy_start (1'b0),
    .io_cpu_dout       (oki1CpuDout),
    .io_rom_rd         (oki1RomRead),
    .io_rom_addr       (oki1RomAddr),
    .io_rom_cache_addr (oki1MappedAddr),
    .io_rom_dout       (oki1RomData),
    .io_rom_valid      (oki1RomDataValid),
    .io_audio_valid    (oki1AudioValid),
    .io_audio_bits     (oki1Audio),
    .io_debug_ctrl_bytes (oki1DebugCtrlBytes),
    .io_debug_body_bytes (oki1DebugBodyBytes)
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
    .io_irq             (ymzIrq)
  );

  YM2203 ym2203 (
    .clock             (clock),
    .reset             (reset | ~pwrinst2Z80Sound),
    .io_cpu_wr         (ym2203Write),
    .io_cpu_addr       (cpuAddr[0]),
    .io_cpu_din        (cpuDout),
    .io_cpu_dout       (ym2203CpuDout),
    .io_irq            (ym2203Irq),
    .io_audio_valid    (ym2203AudioValid),
    .io_audio_bits_psg (ym2203PsgAudio),
    .io_audio_bits_fm  (ym2203FmAudio)
  );

  CaveSoundRomReadArbiter arbiter (
    .clock          (clock),
    .reset          (reset),
    .io_in_0_rd     (soundDeviceIsOki & ~pwrinst2Z80Sound & oki0RomRead),
    .io_in_0_addr   (oki0MappedAddr),
    .io_in_0_dout   (oki0RomDout),
    .io_in_0_valid  (oki0RomValid),
    .io_in_1_rd     (soundDeviceIsYmz & ymzRomRd),
    .io_in_1_addr   ({1'b0, ymzRomAddr}),
    .io_in_1_dout   (ymzRomDout),
    .io_in_1_wait_n (ymzRomWaitN),
    .io_in_1_valid  (ymzRomValid),
    .io_in_2_rd     (z80ProgRomArbiterRead),
    .io_in_2_addr   ({9'h000, cpuAddr}),
    .io_in_2_dout   (progRomDout),
    .io_in_2_valid  (progRomValid),
    .io_in_3_rd     (z80BankRomArbiterRead),
    .io_in_3_addr   ({8'h00, z80BankReg, cpuAddr[13:0]}),
    .io_in_3_dout   (bankRomDout),
    .io_in_3_valid  (bankRomValid),
    .io_out_rd      (io_rom_0_rd),
    .io_out_addr    (io_rom_0_addr),
    .io_out_dout    (io_rom_0_dout),
    .io_out_wait_n  (io_rom_0_wait_n),
    .io_out_valid   (io_rom_0_valid)
  );

  AudioMixer io_audio_mixer (
    .clock       (clock),
    .io_pwrinst2 (pwrinst2Z80Sound),
    .io_in_4     (oki1Mix),
    .io_in_3     (oki0Mix),
    .io_in_2     (pwrinst2Z80Sound ? ym2203FmMix : 16'h0000),
    .io_in_1     (pwrinst2Z80Sound ? ym2203PsgMix : 16'h0000),
    .io_in_0     (soundDeviceIsYmz ? ymzAudioReg : 16'h0000),
    .io_out      (io_audio)
  );

  assign cpuInt = pwrinst2Z80Sound & ym2203Irq;
  assign io_ctrl_irq = soundDeviceIsYmz & ymzIrq;
  assign io_ctrl_oki_0_dout = {8'h00, oki0CpuDout};
  assign io_ctrl_oki_1_dout = {8'h00, oki1CpuDout};
  assign io_ctrl_ymz_dout = {8'h00, ymzCpuDout};
  assign io_ctrl_reply = (replyCount == 6'd0) ? 16'h00ff : {8'h00, replyFifo[replyReadPtr]};
  assign io_ctrl_reply_empty = replyCount == 6'd0;

`ifdef CAVE_ENABLE_DEBUG_OVERLAY
  assign io_debug = {
    debugOki1StatusRead,
    debugOki0StatusRead,
    debugYmFmPeak,
    debugYmPsgPeak,
    debugYmFlags,
    debugLastYmData,
    debugLastYmReg,
    8'hAC
  };
`else
  assign io_debug = 64'd0;
`endif

  assign io_rom_1_rd = pwrinst2Z80Sound ? oki0RomRead : 1'b1;
  assign io_rom_1_addr = pwrinst2Z80Sound ? oki0MappedAddr : oki1MappedAddr;
  assign io_rom_2_rd = pwrinst2Z80Sound & oki1RomRead;
  assign io_rom_2_addr = oki1MappedAddr;
endmodule
