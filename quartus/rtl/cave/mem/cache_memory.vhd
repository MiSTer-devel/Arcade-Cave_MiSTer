-------------------------------------------------------------------------------
--
-- Copyright (c) 2019 Rick Wertenbroek <rick.wertenbroek@gmail.com>
-- All rights reserved.
--
-- Redistribution and use in source and binary forms, with or without
-- modification, are permitted provided that the following conditions are met:
--
-- 1. Redistributions of source code must retain the above copyright notice,
-- this list of conditions and the following disclaimer.
--
-- 2. Redistributions in binary form must reproduce the above copyright notice,
-- this list of conditions and the following disclaimer in the documentation
-- and/or other materials provided with the distribution.
--
-- 3. Neither the name of the copyright holder nor the names of its
-- contributors may be used to endorse or promote products derived from this
-- software without specific prior written permission.
--
-- THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
-- AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
-- IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
-- ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
-- LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
-- CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
-- SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
-- INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
-- CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
-- ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
-- POSSIBILITY OF SUCH DAMAGE.
--
-------------------------------------------------------------------------------
-- File         : cache_memory.vhd
-- Description  : This is a simple (yet somewhat generic) cache memory.
--
-- Author       : Rick Wertenbroek
-- Version      : 0.0
--
-- VHDL std     : 2008
-- Dependencies : log_pkg.vhd
-------------------------------------------------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

use work.log_pkg.all;

entity cache_memory is
    generic (
        REGISTER_OUT          : boolean := false; -- Register on the outputs
        ADDRESS_BITS          : natural := 24;
        LOG_CACHE_LINES       : natural := 9;
        WORD_BYTE_SIZE        : natural := 2;
        CACHE_LINE_WORDS      : natural := 16;
        PERF_COUNT_EN         : boolean := false -- Perf Counters are tied to
                                                 -- 0 if false, leave them open
        );
    port (
        clk_i                   : in  std_logic;
        rst_i                   : in  std_logic;
        -- Agent - Cache
        -- Address should be stable from when read is asserted and remain
        -- stable until valid is asserted
        agent_to_cache_addr_i   : in  unsigned(ADDRESS_BITS-1 downto 0);
        agent_to_cache_read_i   : in  std_logic;
        cache_to_agent_data_o   : out std_logic_vector(WORD_BYTE_SIZE*8-1 downto 0);
        cache_to_agent_valid_o  : out std_logic;
        -- Cache - Memory
        cache_to_memory_addr_o  : out unsigned(ADDRESS_BITS-1 downto 0);
        cache_to_memory_read_o  : out std_logic;
        memory_to_cache_data_i  : in  std_logic_vector(WORD_BYTE_SIZE*8*CACHE_LINE_WORDS-1 downto 0);
        memory_to_cache_valid_i : in  std_logic;
        -- Perf Counters
        req_counter_o           : out std_logic_vector(31 downto 0);
        miss_counter_o          : out std_logic_vector(31 downto 0)
        );
end entity cache_memory;

architecture rtl of cache_memory is

    ---------------
    -- Constants --
    ---------------
    constant CACHE_LINES_C      : natural := 2**LOG_CACHE_LINES;

    -- Using an integer log rounded up or down should not change anything here
    -- since these must be powers of two (there are assertions below).
    constant BYTE_SELECT_BITS_C : natural := ilogup(WORD_BYTE_SIZE);
    constant WORD_SELECT_BITS_C : natural := ilogup(CACHE_LINE_WORDS);
    constant LINE_SELECT_BITS_C : natural := LOG_CACHE_LINES;
    constant TAG_SIZE_C         : natural := ADDRESS_BITS - (BYTE_SELECT_BITS_C + WORD_SELECT_BITS_C + LINE_SELECT_BITS_C);
    constant WORD_SIZE_C        : natural := WORD_BYTE_SIZE*8;

    -----------
    -- Types --
    -----------
    type state_t               is (IDLE, CHECK_BRAM, WAIT_MEM);
    subtype cache_word_index_t is unsigned(WORD_SELECT_BITS_C-1 downto 0);
    subtype cache_word_t       is std_logic_vector(WORD_SIZE_C-1 downto 0);
    subtype cache_line_index_t is unsigned(LINE_SELECT_BITS_C-1 downto 0);
    subtype cache_line_t       is std_logic_vector(WORD_SIZE_C*CACHE_LINE_WORDS-1 downto 0);
    subtype tag_t              is unsigned(TAG_SIZE_C-1 downto 0);
    -- BRAM Line is [valid, tag, cache line]
    subtype bram_line_t        is std_logic_vector(TAG_SIZE_C+cache_line_t'length downto 0);

    type bram_line_array_t     is array (natural range <>) of bram_line_t;

    type data_request_info_t is record
        c_tag  : tag_t;
        c_line : cache_line_index_t;
        c_word : cache_word_index_t;
    end record data_request_info_t;

    type cache_line_info_t is record
        c_valid : std_logic;
        c_tag   : tag_t;
        c_line  : cache_line_t;
    end record cache_line_info_t;

    ---------------
    -- Functions --
    ---------------

    -- Extract tag, line, word from address
    function request_addr_to_info(addr_i : unsigned(ADDRESS_BITS-1 downto 0)) return data_request_info_t is
        variable result_v : data_request_info_t;
    begin

        result_v.c_tag  := addr_i(addr_i'high downto addr_i'high-TAG_SIZE_C+1);
        result_v.c_line := addr_i(addr_i'high-TAG_SIZE_C downto addr_i'high-TAG_SIZE_C-LINE_SELECT_BITS_C+1);
        result_v.c_word := addr_i(WORD_SELECT_BITS_C+BYTE_SELECT_BITS_C-1 downto BYTE_SELECT_BITS_C);

        return result_v;
    end request_addr_to_info;

    -- Extract info from bram cache line
    function extract_info_from_bram_line(bram_line_i : bram_line_t) return cache_line_info_t is
        variable cache_line_info_v : cache_line_info_t;
    begin
        cache_line_info_v.c_valid := bram_line_i(bram_line_i'high); -- Validity bit
        cache_line_info_v.c_tag   := unsigned(bram_line_i(bram_line_i'high-1 downto bram_line_i'high-TAG_SIZE_C));
        cache_line_info_v.c_line  := bram_line_i(cache_line_t'high downto cache_line_t'low);

        return cache_line_info_v;
    end extract_info_from_bram_line;

    -------------
    -- Signals --
    -------------
    signal next_state_s           : state_t;
    signal req_info_s             : data_request_info_t;
    signal bram_line_info_s       : cache_line_info_t;
    signal hit_s                  : std_logic;
    signal cache_to_agent_valid_s : std_logic;
    signal cache_to_agent_data_s  : std_logic_vector(cache_to_agent_data_o'range);

    ---------------
    -- Registers --
    ---------------
    signal state_reg_s         : state_t;
    signal validity_bits_s     : std_logic_vector(2**LOG_CACHE_LINES-1 downto 0);
    signal valid_s             : std_logic;

    ----------
    -- BRAM --
    ----------

    -- Initialized for simulation purposes (can be anything on real board, does
    -- not matter...)
    signal bram_s              : bram_line_array_t(2**LOG_CACHE_LINES-1 downto 0) := (others => (others => '0'));
    signal bram_write_enable_s : std_logic;
    signal bram_data_in_s      : bram_line_t;
    signal bram_data_out_s     : bram_line_t;

    signal read_index_s        : integer range 0 to 2**LOG_CACHE_LINES-1;
    signal write_index_s       : integer range 0 to 2**LOG_CACHE_LINES-1;

begin

    ----------------
    -- Assertions --
    ----------------
    assert (2**(ilogup(WORD_BYTE_SIZE)) = WORD_BYTE_SIZE) report "Word Byte Size is not a power of two." severity failure;
    assert (2**(ilogup(CACHE_LINE_WORDS)) = CACHE_LINE_WORDS) report "The number of words in a cache line is not a power of two." severity failure;
    assert (2**(ilogup(CACHE_LINES_C)) = CACHE_LINES_C) report "The number of lines in the cache is not a power of two." severity failure;
    assert (ADDRESS_BITS > (LINE_SELECT_BITS_C + WORD_SELECT_BITS_C + BYTE_SELECT_BITS_C)) report "The cache must be smaller than the memory" severity failure;

    -- Extract the cache info (tag, line, word) we need from address
    req_info_s <= request_addr_to_info(agent_to_cache_addr_i);

    ---------
    -- FSM --
    ---------
    fsm_reg_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if rst_i = '1' then
                state_reg_s <= IDLE;
            else
                state_reg_s <= next_state_s;
            end if; -- Reset
        end if; -- Rising Edge Clock
    end process fsm_reg_process;

    fsm_comb_process : process(all) is
    begin

        next_state_s <= state_reg_s; -- Default, stay

        case state_reg_s is
            when IDLE =>
                -- If there is a read request, check internal BRAM (cache)
                if agent_to_cache_read_i = '1' then
                    next_state_s <= CHECK_BRAM;
                end if;

            when CHECK_BRAM =>
                -- If there is a hit we are done
                if hit_s = '1' then
                    next_state_s <= IDLE;
                -- Else wait for the memory to give us the data
                else
                    next_state_s <= WAIT_MEM;
                end if;

            when WAIT_MEM =>
                -- When the memory got us the data go to check BRAM (cache)
                -- it will hit (we read from BRAM, this uses less hardware at
                -- the cost of one clock cycle).
                if memory_to_cache_valid_i = '1' then
                    next_state_s <= CHECK_BRAM;
                end if;

            when others =>
                null;

        end case;

    end process fsm_comb_process;

    -- Issue a memory read when we miss (hit is '0')
    cache_to_memory_read_o <= '1' when (state_reg_s = CHECK_BRAM) and (hit_s = '0') else
                              '0';
    -- Address to memory is address with LSB bits set to zero (in order to get
    -- a whole aligned line from the memory)
    cache_to_memory_addr_o <= agent_to_cache_addr_i(agent_to_cache_addr_i'high downto BYTE_SELECT_BITS_C+WORD_SELECT_BITS_C) & to_unsigned(0, BYTE_SELECT_BITS_C+WORD_SELECT_BITS_C);

    ----------
    -- BRAM --
    ----------
    read_index_s        <= to_integer(req_info_s.c_line);
    write_index_s       <= to_integer(req_info_s.c_line);
    --
    bram_write_enable_s <= memory_to_cache_valid_i;
    bram_data_in_s      <= '1' & std_logic_vector(req_info_s.c_tag) & memory_to_cache_data_i;

    -- Maybe better if this was a component of it's own, however this does
    -- indeed generate a correct BRAM (write first) with Vivado 2017.4.1
    bram_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            bram_data_out_s <= bram_s(read_index_s);

            if bram_write_enable_s = '1' then
                bram_s(write_index_s) <= bram_data_in_s;
                bram_data_out_s       <= bram_data_in_s; -- Write First
            end if;

        end if; -- Rising Edge Clock
    end process bram_process;

    valid_regs_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if rst_i = '1' then
                validity_bits_s <= (others => '0');
            elsif bram_write_enable_s = '1' then
                validity_bits_s(write_index_s) <= '1';
            end if;
        end if;
    end process valid_regs_process;

    valid_reg_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if rst_i = '1' then
                valid_s <= '0';
            else
                valid_s <= validity_bits_s(read_index_s);
            end if;
        end if;
    end process valid_reg_process;

    -- Extract info from bram line
    bram_line_info_s <= extract_info_from_bram_line(bram_data_out_s);

    -- Hit (When we check the BRAM (cache) and the data is valid and the tags
    -- are right.
    hit_s <= '1' when (state_reg_s = CHECK_BRAM) and (valid_s = '1') and (bram_line_info_s.c_tag = req_info_s.c_tag) else
             '0';
    --hit_s <= '1' when (state_reg_s = CHECK_BRAM) and (bram_line_info_s.c_valid = '1') and (bram_line_info_s.c_tag = req_info_s.c_tag) else
    --         '0';

    -- To Agent
    cache_comb_process : process(all) is
        variable selected_word_v : natural;
    begin
        selected_word_v := to_integer(req_info_s.c_word);

        cache_to_agent_data_s <= bram_line_info_s.c_line((selected_word_v+1)*WORD_SIZE_C-1 downto selected_word_v*WORD_SIZE_C);
    end process cache_comb_process;

    cache_to_agent_valid_s <= hit_s;

    dbg_gen : if REGISTER_OUT generate
        process(clk_i) is
        begin
            if rising_edge(clk_i) then
                if rst_i = '1' then
                    cache_to_agent_valid_o <= '0';
                else
                    cache_to_agent_valid_o <= cache_to_agent_valid_s;
                end if;
                cache_to_agent_data_o <= cache_to_agent_data_s;
            end if;
        end process;
    else generate
        cache_to_agent_valid_o <= cache_to_agent_valid_s;
        cache_to_agent_data_o  <= cache_to_agent_data_s;
    end generate;

    -------------------
    -- Perf Counters --
    -------------------
    perf_counter_generate : if PERF_COUNT_EN generate
        perf_counter_block : block
            signal req_counter_s  : unsigned(31 downto 0);
            signal miss_counter_s : unsigned(31 downto 0);
        begin
            perf_counter_process : process(clk_i) is
            begin
                if rising_edge(clk_i) then
                    if rst_i = '1' then
                        req_counter_s  <= (others => '0');
                        miss_counter_s <= (others => '0');
                    else
                        if agent_to_cache_read_i = '1' then
                            req_counter_s <= req_counter_s + 1;
                        end if; -- Request Count

                        if (state_reg_s = CHECK_BRAM) and (hit_s = '0') then
                            miss_counter_s <= miss_counter_s + 1;
                        end if; -- Miss Count
                    end if; -- Reset
                end if; -- Rising Edge Clock
            end process perf_counter_process;

            req_counter_o  <= std_logic_vector(req_counter_s);
            miss_counter_o <= std_logic_vector(miss_counter_s);
        end block perf_counter_block;
    else generate
        req_counter_o  <= (others => '0');
        miss_counter_o <= (others => '0');
    end generate;

end architecture rtl;
