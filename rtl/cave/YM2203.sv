// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

module YM2203(
  input         clock,
  input         reset,
  input         io_cpu_wr,
  input         io_cpu_addr,
  input  [7:0]  io_cpu_din,
  output [7:0]  io_cpu_dout,
  output        io_cpu_wait_n,
  output        io_cpu_queue_full,
  output        io_irq,
  output        io_audio_valid,
  output [15:0] io_audio_bits_psg,
  output [15:0] io_audio_bits_fm
);
  wire       ym_cen;
  wire       ym_irq_n;
  wire [9:0] ym_psg_snd;
  reg        writeActiveReg;
  reg        writeQueuedReg;
  reg        writeAddrReg;
  reg        writeQueuedAddrReg;
  reg  [7:0] writeDataReg;
  reg  [7:0] writeQueuedDataReg;
  wire       writeComplete = writeActiveReg & ym_cen;
  wire       writeCanAccept = ~writeActiveReg | ~writeQueuedReg | writeComplete;

  CaveClockEnable #(
    .STEP (17'h2000)
  ) clock_enable (
    .clock  (clock),
    .enable (ym_cen)
  );

  jt03 ym2203 (
    .rst        (reset),
    .clk        (clock),
    .cen        (ym_cen),
    .din        (writeActiveReg ? writeDataReg : io_cpu_din),
    .addr       (writeActiveReg ? writeAddrReg : io_cpu_addr),
    .cs_n       (1'b0),
    .wr_n       (~writeActiveReg),
    .dout       (io_cpu_dout),
    .irq_n      (ym_irq_n),
    .IOA_in     (8'h00),
    .IOB_in     (8'h00),
    .psg_A      (),
    .psg_B      (),
    .psg_C      (),
    .fm_snd     (io_audio_bits_fm),
    .psg_snd    (ym_psg_snd),
    .snd        (),
    .snd_sample (io_audio_valid),
    .debug_view ()
  );

  always @(posedge clock) begin
    if (reset) begin
      writeActiveReg <= 1'b0;
      writeQueuedReg <= 1'b0;
      writeAddrReg <= 1'b0;
      writeQueuedAddrReg <= 1'b0;
      writeDataReg <= 8'h00;
      writeQueuedDataReg <= 8'h00;
    end
    else begin
      if (writeComplete) begin
        if (writeQueuedReg) begin
          writeActiveReg <= 1'b1;
          writeAddrReg <= writeQueuedAddrReg;
          writeDataReg <= writeQueuedDataReg;
          writeQueuedReg <= 1'b0;
        end
        else begin
          writeActiveReg <= 1'b0;
        end
      end

      if (io_cpu_wr) begin
        if (~writeActiveReg | (writeComplete & ~writeQueuedReg)) begin
          writeActiveReg <= 1'b1;
          writeAddrReg <= io_cpu_addr;
          writeDataReg <= io_cpu_din;
        end
        else if (~writeQueuedReg | writeComplete) begin
          writeQueuedReg <= 1'b1;
          writeQueuedAddrReg <= io_cpu_addr;
          writeQueuedDataReg <= io_cpu_din;
        end
      end
    end
  end

  assign io_cpu_wait_n = writeCanAccept;
  assign io_cpu_queue_full = writeActiveReg & writeQueuedReg & ~writeComplete;
  assign io_irq = ~ym_irq_n;
  assign io_audio_bits_psg = {1'b0, ym_psg_snd, 5'b0};
endmodule
