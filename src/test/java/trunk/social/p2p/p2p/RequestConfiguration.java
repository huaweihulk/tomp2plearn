package trunk.social.p2p.p2p;

public interface RequestConfiguration {

	public abstract int parallel();

	public abstract boolean isForceUPD();

	public abstract boolean isForceTCP();

}