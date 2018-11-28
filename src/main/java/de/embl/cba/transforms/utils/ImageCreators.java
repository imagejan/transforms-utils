package de.embl.cba.transforms.utils;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public abstract class ImageCreators
{
	public static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< T > copyAsArrayImg( RandomAccessibleInterval< T > orig )
	{
		RandomAccessibleInterval< T > copy = new ArrayImgFactory( orig.randomAccess().get() ).create( orig );
		copy = Transforms.getWithAdjustedOrigin( orig, copy );
		LoopBuilder.setImages( copy, orig ).forEachPixel( ( c, o ) -> c.set( o ) );

		return copy;
	}

	public static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< T > createEmptyArrayImg( RandomAccessibleInterval< T > rai )
	{
		RandomAccessibleInterval< T > newImage = new ArrayImgFactory( rai.randomAccess().get() ).create( rai );
		newImage = Transforms.getWithAdjustedOrigin( rai, newImage );
		return newImage;
	}
}