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
-- File         : log_pkg.vhd
-- Description  : Package to provide integer logarithms.
--
-- Author       : Rick Wertenbroek
-- Version      : 1.0
--
-- Dependencies : -
-------------------------------------------------------------------------------

library ieee;

package log_pkg is

    -- integer logarithm (rounded up)
    function ilogup (x : natural; base : natural := 2) return natural;

    -- integer logarithm (rounded down)
    function ilog (x : natural; base : natural := 2) return natural;

end log_pkg;

package body log_pkg is

    -- integer logarithm (rounded up)
    function ilogup (x : natural; base : natural := 2) return natural is
        variable y : natural := 0;
    begin
        assert (base > 1) report "Base cannot be less than 2." severity error;
        while x > base ** y loop
            y := y + 1;
        end loop;
        return y;
    end ilogup;

    -- integer logarithm (rounded down)
    function ilog (x : natural; base : natural := 2) return natural is
        variable y : natural := 1;
    begin
        assert (base > 1) report "Base cannot be less than 2." severity error;
        while x > base**y loop
            y := y + 1;
        end loop;
        if x < base**y then
            y := y - 1;
        end if;
        return y;
    end ilog;

end log_pkg;
