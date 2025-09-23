/*
 * Rex — Remote Exec for Android
 * Copyright (C) 2024 Rex Maintainers (b3p3k0)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.rex.app.core

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class DangerousCommandValidatorTest {

    private lateinit var validator: DangerousCommandValidator

    @Before
    fun setup() {
        validator = DangerousCommandValidator()
    }

    @Test
    fun `detects dangerous rm commands`() {
        assertTrue(validator.isDangerous("rm -rf /"))
        assertTrue(validator.isDangerous("rm -f /home/user"))
        assertTrue(validator.isDangerous("RM -RF /tmp"))
        assertTrue(validator.isDangerous("rm --recursive --force /var"))
    }

    @Test
    fun `detects filesystem commands`() {
        assertTrue(validator.isDangerous("mkfs.ext4 /dev/sda1"))
        assertTrue(validator.isDangerous("dd if=/dev/zero of=/dev/sda"))
        assertTrue(validator.isDangerous("fdisk /dev/sda"))
        assertTrue(validator.isDangerous("FORMAT C:"))
    }

    @Test
    fun `detects system control commands`() {
        assertTrue(validator.isDangerous("shutdown -h now"))
        assertTrue(validator.isDangerous("reboot"))
        assertTrue(validator.isDangerous("killall ssh"))
        assertTrue(validator.isDangerous("init 0"))
    }

    @Test
    fun `detects fork bombs`() {
        assertTrue(validator.isDangerous(":(){ :|:& };:"))
        assertTrue(validator.isDangerous("while true; do echo bomb; done"))
        assertTrue(validator.isDangerous("for(;;){ fork(); }"))
    }

    @Test
    fun `detects network disruption`() {
        assertTrue(validator.isDangerous("iptables -F"))
        assertTrue(validator.isDangerous("ufw disable"))
        assertTrue(validator.isDangerous("ifconfig eth0 down"))
        assertTrue(validator.isDangerous("firewall-cmd --flush"))
    }

    @Test
    fun `detects indirect execution`() {
        assertTrue(validator.isDangerous("sudo rm -rf /"))
        assertTrue(validator.isDangerous("sh -c 'rm -rf /home'"))
        assertTrue(validator.isDangerous("bash -c \"dd if=/dev/zero\""))
        assertTrue(validator.isDangerous("eval 'mkfs.ext4 /dev/sda'"))
    }

    @Test
    fun `allows safe commands`() {
        assertFalse(validator.isDangerous("ls -la"))
        assertFalse(validator.isDangerous("cat /etc/hostname"))
        assertFalse(validator.isDangerous("ps aux"))
        assertFalse(validator.isDangerous("grep pattern file.txt"))
        assertFalse(validator.isDangerous("mkdir new_dir"))
        assertFalse(validator.isDangerous("cp file1 file2"))
    }

    @Test
    fun `handles edge cases`() {
        assertFalse(validator.isDangerous(""))
        assertFalse(validator.isDangerous("   "))
        assertFalse(validator.isDangerous("# rm -rf /"))
        assertFalse(validator.isDangerous("echo 'rm -rf /'"))
    }

    @Test
    fun `provides danger reasons`() {
        assertEquals("File deletion command detected",
            validator.getDangerReason("rm -rf /"))
        assertEquals("Filesystem formatting command detected",
            validator.getDangerReason("mkfs.ext4 /dev/sda"))
        assertEquals("System control command detected",
            validator.getDangerReason("shutdown now"))
        assertNull(validator.getDangerReason("ls -la"))
    }

    @Test
    fun `detects case variations`() {
        assertTrue(validator.isDangerous("RM -RF /"))
        assertTrue(validator.isDangerous("Sudo rm -rf /"))
        assertTrue(validator.isDangerous("SHUTDOWN"))
        assertTrue(validator.isDangerous("DD if=/dev/zero"))
    }

    @Test
    fun `detects spacing variations`() {
        assertTrue(validator.isDangerous("rm  -rf  /"))
        assertTrue(validator.isDangerous("sudo    rm -rf /"))
        assertTrue(validator.isDangerous("dd    if=/dev/zero"))
    }
}