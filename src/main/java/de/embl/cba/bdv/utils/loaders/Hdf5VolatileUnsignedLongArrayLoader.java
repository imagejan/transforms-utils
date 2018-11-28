package de.embl.cba.bdv.utils.loaders;

import bdv.img.cache.CacheArrayLoader;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileLongArray;

public class Hdf5VolatileUnsignedLongArrayLoader implements CacheArrayLoader< VolatileLongArray >
{
	private final IHDF5UnsignedLongAccess hdf5Access;

	public Hdf5VolatileUnsignedLongArrayLoader( final IHDF5UnsignedLongAccess hdf5Access )
	{
		this.hdf5Access = hdf5Access;
	}

	@Override
	public VolatileLongArray loadArray( final int timepoint, final int setup, final int level, final int[] dimensions, final long[] min ) throws InterruptedException
	{
		final long[] array = hdf5Access.readLongMDArrayBlockWithOffset( timepoint, setup, level, dimensions, min );
		return new VolatileLongArray( array, true );
	}

	@Override
	public int getBytesPerElement()
	{
		return 2;
	}


}

