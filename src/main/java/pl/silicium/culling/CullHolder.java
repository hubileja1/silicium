package pl.silicium.culling;

/** Duck interface mixed into Entity and BlockEntity so each one carries its own CullTarget (no map lookups per frame). */
public interface CullHolder {
	CullTarget silicium$cullTarget();
}
