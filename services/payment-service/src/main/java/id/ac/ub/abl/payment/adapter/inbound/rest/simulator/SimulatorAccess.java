package id.ac.ub.abl.payment.adapter.inbound.rest.simulator;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
public class SimulatorAccess {
    private final byte[] expected;
    public SimulatorAccess(String address, String token) {
        try {
            if (address == null || address.isBlank() || !InetAddress.getByName(address).isLoopbackAddress()) {
                throw new IllegalStateException("Simulator membutuhkan server.address loopback eksplisit");
            }
        } catch (java.net.UnknownHostException failure) { throw new IllegalStateException("Binding simulator tidak valid"); }
        if (token == null || token.codePointCount(0, token.length()) < 32 || token.isBlank()) {
            throw new IllegalStateException("Token simulator harus tersedia dan minimal 32 karakter");
        }
        this.expected = digest(token);
    }
    public boolean accepts(String token) { return token != null && MessageDigest.isEqual(expected, digest(token)); }
    private static byte[] digest(String token) {
        try { return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 tidak tersedia", failure); }
    }
}
