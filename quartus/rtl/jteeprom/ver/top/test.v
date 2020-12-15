`timescale 1ns/1ps

module test;

reg clk, rst, sclk, di, cs;
integer cnt, start=80;
wire    do;

localparam CMDCNT=3;

reg [2:0] cmd[0:64*CMDCNT-1];

initial begin
    $readmemb("read_110010.bin", cmd,0,63);
    $readmemb("write_110010.bin", cmd,64,64*2-1);
    $readmemb("read_110010.bin", cmd,64*2,64*3-1);
end

initial begin
    clk  = 0;
    di   = 0;
    cs   = 0;
    sclk = 0;
    cnt  = 0;
    forever #20 clk = ~clk;
end

initial begin
    rst = 0;
    #50 rst = 1;
    #50 rst = 0;
end

always @(posedge clk) begin
    if( start ) start = start-1;
    else begin
        { cs, sclk, di } <= cmd[cnt];
        cnt <= cnt+1;
        if( cnt == 64*CMDCNT-1 ) $finish;
    end
end

reg [15:0] read_data;

always @(negedge sclk)
    read_data <= { read_data[14:0], do };

jt9346 UUT(
    .clk    ( clk   ),
    .rst    ( rst   ),
    .sclk   ( sclk  ),
    .di     ( di    ),
    .do     ( do    ),
    .cs     ( cs    )
);

initial begin
    $dumpfile("test.lxt");
    $dumpvars;
end

endmodule