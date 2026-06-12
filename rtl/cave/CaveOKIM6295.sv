// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

module CaveOKIM6295 #(
  parameter INTERPOL = 1,
  parameter WRITE_HOLD_CYCLES = 8
)(
  input         clock,
  input         reset,
  input  [16:0] io_cen_step,
  input         io_ss,
  input         io_cpu_wr,
  input  [7:0]  io_cpu_din,
  input         io_stretch_cpu_wr,
  input         io_wait_for_rom,
  input         io_ignore_busy_start,
  output [7:0]  io_cpu_dout,
  output        io_rom_rd,
  output [17:0] io_rom_addr,
  input  [24:0] io_rom_cache_addr,
  input  [7:0]  io_rom_dout,
  input         io_rom_valid,
  output        io_audio_valid,
  output [13:0] io_audio_bits,
  output [47:0] io_debug_ctrl_bytes,
  output [47:0] io_debug_body_bytes
);
  reg        adpcm_cen;
  reg [15:0] cenAccumulator;
  reg [3:0]  writeHold;
  reg [7:0]  writeDataReg;
  reg [24:0] requestedRomAddr;
  reg [7:0]  romDataReg;
  reg        romDataReady;
  wire [16:0] cenNext = {1'b0, cenAccumulator} + io_cen_step;
  wire        romAddrChanged = io_rom_cache_addr != requestedRomAddr;
  wire        bufferRomForWait = io_wait_for_rom;
  wire        bufferedRomReady = romDataReady & ~romAddrChanged;
  wire        chipRomOk = (io_wait_for_rom & bufferRomForWait) ? bufferedRomReady : io_rom_valid;
  wire [7:0]  chipRomData = (io_wait_for_rom & bufferRomForWait) ? romDataReg : io_rom_dout;
  wire        hold_for_rom = io_wait_for_rom & cenNext[16] & ~chipRomOk;
  localparam integer WRITE_HOLD_RELOAD =
    WRITE_HOLD_CYCLES <= 1 ? 0 :
    WRITE_HOLD_CYCLES > 16 ? 15 :
    WRITE_HOLD_CYCLES - 1;

  wire        stretchCpuWr = io_stretch_cpu_wr & (WRITE_HOLD_CYCLES > 1);
  wire        chipCpuWr = stretchCpuWr ? (io_cpu_wr | (writeHold != 4'd0)) : io_cpu_wr;
  wire [7:0]  chipCpuDin = stretchCpuWr ? (io_cpu_wr ? io_cpu_din : writeDataReg) : io_cpu_din;

  always @(posedge clock) begin
    if (reset) begin
      cenAccumulator <= 16'h0;
      adpcm_cen <= 1'b0;
      requestedRomAddr <= 25'h0;
      romDataReg <= 8'h00;
      romDataReady <= 1'b0;
    end
    else if (hold_for_rom) begin
      adpcm_cen <= 1'b0;
    end
    else begin
      cenAccumulator <= cenNext[15:0];
      adpcm_cen <= cenNext[16];
    end

    if (~reset) begin
      if (romAddrChanged) begin
        requestedRomAddr <= io_rom_cache_addr;
        romDataReady <= 1'b0;
      end
      else if (io_rom_valid) begin
        romDataReg <= io_rom_dout;
        romDataReady <= 1'b1;
      end
    end
  end

  always @(posedge clock) begin
    if (reset) begin
      writeHold <= 4'd0;
      writeDataReg <= 8'h00;
    end
    else if (io_cpu_wr) begin
      writeHold <= WRITE_HOLD_RELOAD;
      writeDataReg <= io_cpu_din;
    end
    else if (writeHold != 4'd0) begin
      writeHold <= writeHold - 4'd1;
    end
  end

  CaveOKIM6295Core #(
    .INTERPOL (INTERPOL)
  ) adpcm (
    .rst          (reset),
    .clk          (clock),
    .cen          (adpcm_cen),
    .ss           (io_ss),
    .wait_for_rom (io_wait_for_rom),
    .ignore_busy_start (io_ignore_busy_start),
    .wrn          (~chipCpuWr),
    .din          (chipCpuDin),
    .dout         (io_cpu_dout),
    .rom_addr     (io_rom_addr),
    .rom_data     (chipRomData),
    .rom_ok       (chipRomOk),
    .sound        (io_audio_bits),
    .sample       (io_audio_valid),
    .debug_ctrl_bytes (io_debug_ctrl_bytes),
    .debug_body_bytes (io_debug_body_bytes)
  );

  assign io_rom_rd = (io_wait_for_rom & bufferRomForWait) ? (romAddrChanged | ~romDataReady) : 1'b1;
endmodule

module CaveOKIM6295Core #(
  parameter INTERPOL = 1
)(
  input                rst,
  input                clk,
  input                cen,
  input                ss,
  input                wait_for_rom,
  input                ignore_busy_start,
  input                wrn,
  input         [7:0]  din,
  output        [7:0]  dout,
  output        [17:0] rom_addr,
  input         [7:0]  rom_data,
  input                rom_ok,
  output signed [13:0] sound,
  output               sample,
  output reg    [47:0] debug_ctrl_bytes,
  output        [47:0] debug_body_bytes
);
  wire        cen_sr;
  wire        cen_sr4;
  wire        cen_sr4b;
  wire        cen_sr32;
  wire [3:0]  busy;
  wire [3:0]  ack;
  wire [3:0]  start;
  wire [3:0]  serial_start;
  wire [3:0]  busy_start;
  wire [3:0]  ctrl_ack;
  wire [3:0]  stop;
  wire [17:0] start_addr;
  wire [17:0] stop_addr;
  wire [17:0] ch_addr;
  wire [9:0]  ctrl_addr;
  wire [7:0]  ch_data;
  wire [7:0]  ctrl_data;
  wire [3:0]  pipe_data;
  wire [3:0]  att;
  wire [3:0]  pipe_att;
  wire        ctrl_ok;
  wire        zero;
  wire        pipe_en;
  wire signed [11:0] pipe_snd;
  wire        debug_start = |serial_start;

  assign busy_start = start & busy;
  assign serial_start = ignore_busy_start ? (start & ~busy) : start;
  assign ctrl_ack = ignore_busy_start ? (ack | busy_start) : ack;
  assign dout = {4'hf, busy | serial_start};

  jt6295_timing u_timing(
    .clk      (clk),
    .cen      (cen),
    .ss       (ss),
    .cen_sr   (cen_sr),
    .cen_sr4  (cen_sr4),
    .cen_sr4b (cen_sr4b),
    .cen_sr32 (cen_sr32)
  );

  CaveOKIM6295Rom u_rom(
    .rst          (rst),
    .clk          (clk),
    .cen4         (cen_sr4),
    .cen32        (cen_sr32),
    .wait_for_rom (wait_for_rom),
    .adpcm_addr   (ch_addr),
    .ctrl_addr    ({8'd0, ctrl_addr}),
    .adpcm_dout   (ch_data),
    .ctrl_dout    (ctrl_data),
    .ctrl_ok      (ctrl_ok),
    .debug_capture_start (debug_start),
    .debug_capture_addr  (start_addr),
    .debug_body_bytes    (debug_body_bytes),
    .rom_addr     (rom_addr),
    .rom_data     (rom_data),
    .rom_ok       (rom_ok)
  );

  jt6295_ctrl u_ctrl(
    .rst        (rst),
    .clk        (clk),
    .cen1       (cen_sr),
    .cen4       (cen_sr4),
    .wrn        (wrn),
    .din        (din),
    .start_addr (start_addr),
    .stop_addr  (stop_addr),
    .att        (att),
    .rom_addr   (ctrl_addr),
    .rom_data   (ctrl_data),
    .rom_ok     (ctrl_ok),
    .start      (start),
    .stop       (stop),
    .busy       (busy),
    .ack        (ctrl_ack),
    .zero       (zero)
  );

  jt6295_serial u_serial(
    .rst        (rst),
    .clk        (clk),
    .cen        (cen_sr),
    .cen4       (cen_sr4),
    .start_addr (start_addr),
    .stop_addr  (stop_addr),
    .att        (att),
    .start      (serial_start),
    .stop       (stop),
    .busy       (busy),
    .ack        (ack),
    .zero       (zero),
    .rom_addr   (ch_addr),
    .rom_data   (ch_data),
    .pipe_en    (pipe_en),
    .pipe_att   (pipe_att),
    .pipe_data  (pipe_data)
  );

  jt6295_adpcm u_adpcm(
    .rst   (rst),
    .clk   (clk),
    .cen   (cen_sr4),
    .en    (pipe_en),
    .att   (pipe_att),
    .data  (pipe_data),
    .sound (pipe_snd)
  );

  jt6295_acc #(.INTERPOL(INTERPOL)) u_acc(
    .rst       (rst),
    .clk       (clk),
    .cen       (cen_sr),
    .cen4      (cen_sr4),
    .sound_in  (pipe_snd),
    .sound_out (sound),
    .sample    (sample)
  );

  always @(posedge clk) begin
    if (rst) begin
      debug_ctrl_bytes <= 48'h000000000000;
    end
    else if (debug_start) begin
      debug_ctrl_bytes <= {
        stop_addr[7:0],
        stop_addr[15:8],
        {6'b000000, stop_addr[17:16]},
        start_addr[7:0],
        start_addr[15:8],
        {6'b000000, start_addr[17:16]}
      };
    end
  end
endmodule

module CaveOKIM6295Rom(
  input         rst,
  input         clk,
  input         cen4,
  input         cen32,
  input         wait_for_rom,
  input  [17:0] adpcm_addr,
  input  [17:0] ctrl_addr,
  output reg [7:0]  adpcm_dout,
  output reg [7:0]  ctrl_dout,
  output reg        ctrl_ok,
  input             debug_capture_start,
  input      [17:0] debug_capture_addr,
  output reg [47:0] debug_body_bytes,
  output reg [17:0] rom_addr,
  input      [7:0]  rom_data,
  input             rom_ok
);
  reg [7:0] st;
  reg [1:0] wait2;
  reg       debug_body_capture;
  reg [2:0] debug_body_count;
  reg [17:0] debug_body_next_addr;

  wire new_addr = rom_addr != ctrl_addr;
  wire adpcm_new_addr = rom_addr != adpcm_addr;
  wire adpcm_data_ok = ~wait_for_rom | rom_ok;

  always @(posedge clk) begin
    if (rst)
      st <= 8'h00;
    else if (cen4)
      st <= 8'h80;
    else if (cen32)
      st <= {st[6:0], st[7]};
  end

  always @(posedge clk) begin
    if (rst) begin
      rom_addr <= 18'h0;
      adpcm_dout <= 8'h00;
      ctrl_dout <= 8'h00;
      ctrl_ok <= 1'b0;
      debug_body_bytes <= 48'h000000000000;
      debug_body_capture <= 1'b0;
      debug_body_count <= 3'd0;
      debug_body_next_addr <= 18'h0;
      wait2 <= 2'b0;
    end
    else begin
      if (debug_capture_start) begin
        debug_body_bytes <= 48'h000000000000;
        debug_body_capture <= 1'b1;
        debug_body_count <= 3'd0;
        debug_body_next_addr <= debug_capture_addr;
      end

      case (st)
        8'b00000001,
        8'b00000010: begin
          rom_addr <= adpcm_addr;
          if (adpcm_data_ok & ~adpcm_new_addr) begin
            adpcm_dout <= rom_data;
            if (debug_body_capture & (adpcm_addr == debug_body_next_addr)) begin
              case (debug_body_count)
                3'd0: debug_body_bytes[7:0] <= rom_data;
                3'd1: debug_body_bytes[15:8] <= rom_data;
                3'd2: debug_body_bytes[23:16] <= rom_data;
                3'd3: debug_body_bytes[31:24] <= rom_data;
                3'd4: debug_body_bytes[39:32] <= rom_data;
                default: debug_body_bytes[47:40] <= rom_data;
              endcase
              debug_body_next_addr <= debug_body_next_addr + 18'd1;
              if (debug_body_count == 3'd5)
                debug_body_capture <= 1'b0;
              else
                debug_body_count <= debug_body_count + 3'd1;
            end
          end
          ctrl_ok <= 1'b0;
          wait2 <= 2'b0;
        end
        default: begin
          rom_addr <= ctrl_addr;
          if (wait2 == 2'b11 && !new_addr) begin
            ctrl_ok <= rom_ok;
            if (rom_ok)
              ctrl_dout <= rom_data;
          end
          else begin
            ctrl_ok <= 1'b0;
          end

          if (new_addr)
            wait2 <= 2'b0;
          else
            wait2 <= {wait2[0], 1'b1};
        end
      endcase
    end
  end
endmodule
