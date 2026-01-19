module PageFlipper_1(
  input         clock,
  input         reset,
  input         io_mode,
  input         io_swapRead,
  input         io_swapWrite,
  output [31:0] io_addrRead,
  output [31:0] io_addrWrite
);

  reg  [1:0] rdIndexReg;
  reg  [1:0] wrIndexReg;
  wire       _wrIndexReg_T_11 = rdIndexReg == 2'h1;
  wire       _wrIndexReg_T_7 = wrIndexReg == 2'h1;
  wire       _rdIndexReg_T_11 = wrIndexReg == 2'h1;
  wire       _rdIndexReg_T_7 = rdIndexReg == 2'h1;
  wire       _wrIndexReg_T_27 = rdIndexReg == 2'h1;
  wire       _wrIndexReg_T_23 = wrIndexReg == 2'h1;
  always @(posedge clock) begin
    if (reset) begin
      rdIndexReg <= 2'h0;
      wrIndexReg <= 2'h1;
    end
    else if (io_mode) begin
      if (io_swapRead & io_swapWrite) begin
        rdIndexReg <= wrIndexReg;
        wrIndexReg <=
          ~(|wrIndexReg) & _wrIndexReg_T_11 | _wrIndexReg_T_7 & ~(|rdIndexReg)
            ? 2'h2
            : {1'h0,
               ~(_wrIndexReg_T_7 & rdIndexReg == 2'h2 | wrIndexReg == 2'h2
                 & _wrIndexReg_T_11)};
      end
      else begin
        if (io_swapRead)
          rdIndexReg <=
            ~(|rdIndexReg) & _rdIndexReg_T_11 | _rdIndexReg_T_7 & ~(|wrIndexReg)
              ? 2'h2
              : {1'h0,
                 ~(_rdIndexReg_T_7 & wrIndexReg == 2'h2 | rdIndexReg == 2'h2
                   & _rdIndexReg_T_11)};
        if (io_swapRead | ~io_swapWrite) begin
        end
        else
          wrIndexReg <=
            ~(|wrIndexReg) & _wrIndexReg_T_27 | _wrIndexReg_T_23 & ~(|rdIndexReg)
              ? 2'h2
              : {1'h0,
                 ~(_wrIndexReg_T_23 & rdIndexReg == 2'h2 | wrIndexReg == 2'h2
                   & _wrIndexReg_T_27)};
      end
    end
    else if (io_swapWrite) begin
      rdIndexReg <= {1'h0, wrIndexReg[0]};
      wrIndexReg <= {1'h0, ~(wrIndexReg[0])};
    end
  end // always @(posedge)
  assign io_addrRead = {11'h120, rdIndexReg, 19'h0};
  assign io_addrWrite = {11'h120, wrIndexReg, 19'h0};
endmodule

