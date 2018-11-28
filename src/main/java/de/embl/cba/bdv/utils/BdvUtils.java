package de.embl.cba.bdv.utils;

import bdv.tools.transformation.TransformedSource;
import bdv.util.*;
import bdv.viewer.*;
import bdv.viewer.animate.AbstractTransformAnimator;
import bdv.viewer.animate.SimilarityTransformAnimator;
import bdv.viewer.state.SourceState;
import de.embl.cba.bdv.utils.algorithms.RegionExtractor;
import de.embl.cba.bdv.utils.labels.LabelsSource;
import de.embl.cba.bdv.utils.transforms.ConcatenatedTransformAnimator;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.Duplicator;
import ij3d.Content;
import ij3d.Image3DUniverse;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.*;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.Views;
import org.scijava.vecmath.Color3f;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.DoubleStream;

import static de.embl.cba.bdv.utils.transforms.Transforms.createBoundingIntervalAfterTransformation;

public abstract class BdvUtils
{

	public static final String OVERLAY = "overlay";


	public static FinalInterval getCurrentlyVisibleInterval( Bdv bdv, int sourceId )
	{
		final AffineTransform3D sourceTransform = getSourceTransform( bdv, sourceId );
		final RandomAccessibleInterval< ? > rai = getRandomAccessibleInterval( bdv, sourceId );
		return createBoundingIntervalAfterTransformation( rai, sourceTransform );
	}

	public static void zoomToSource( Bdv bdv, String sourceName )
	{
		zoomToSource( bdv, getSourceIndex( bdv, sourceName ) );
	}

	public static void zoomToSource( Bdv bdv, int sourceId )
	{
		final FinalInterval interval = getInterval( bdv, sourceId );

		zoomToInterval( bdv, interval, 1.0 );
	}

	public static FinalInterval getInterval( Bdv bdv, int sourceId )
	{
		final AffineTransform3D sourceTransform = getSourceTransform( bdv, sourceId );
		final RandomAccessibleInterval< ? > rai = getRandomAccessibleInterval( bdv, sourceId );
		return createBoundingIntervalAfterTransformation( rai, sourceTransform );
	}

	public static FinalRealInterval getCurrentViewerInterval( Bdv bdv )
	{
		AffineTransform3D viewerTransform = new AffineTransform3D();
		bdv.getBdvHandle().getViewerPanel().getState().getViewerTransform( viewerTransform );
		viewerTransform = viewerTransform.inverse();
		final long[] min = new long[ 3 ];
		final long[] max = new long[ 3 ];
		max[ 0 ] = bdv.getBdvHandle().getViewerPanel().getWidth();
		max[ 1 ] = bdv.getBdvHandle().getViewerPanel().getHeight();
		return viewerTransform.estimateBounds( new FinalInterval( min, max ) );
	}

	public static int getSourceIndex( Bdv bdv, Source< ? > source )
	{
		final List< SourceState< ? > > sources = bdv.getBdvHandle().getViewerPanel().getState().getSources();

		for ( int i = 0; i < sources.size(); ++i )
		{
			if ( sources.get( i ).getSpimSource().equals( source ) )
			{
				return i;
			}
		}

		return -1;
	}

	public static String getName( Bdv bdv, int sourceId )
	{
		return bdv.getBdvHandle().getViewerPanel().getState().getSources().get( sourceId ).getSpimSource().getName();
	}

	public static ArrayList< String > getSourceNames( Bdv bdv )
	{
		final ArrayList< String > sourceNames = new ArrayList<>();

		final List< SourceState< ? > > sources = bdv.getBdvHandle().getViewerPanel().getState().getSources();

		for ( SourceState source : sources )
		{
			sourceNames.add( source.getSpimSource().getName() );
		}

		return sourceNames;
	}


	public static int getSourceIndex( Bdv bdv, String sourceName )
	{
		return getSourceNames( bdv ).indexOf( sourceName );
	}

	public static VoxelDimensions getVoxelDimensions( Bdv bdv, int sourceId )
	{
		return bdv.getBdvHandle().getViewerPanel().getState().getSources().get( sourceId ).getSpimSource().getVoxelDimensions();
	}


	public static AffineTransform3D getSourceTransform( Bdv bdv, int sourceId )
	{
		final AffineTransform3D sourceTransform = new AffineTransform3D();
		bdv.getBdvHandle().getViewerPanel().getState().getSources().get( sourceId ).getSpimSource().getSourceTransform( 0, 0, sourceTransform );
		return sourceTransform;
	}


	public static AffineTransform3D getSourceTransform( Source source, int t, int level )
	{
		AffineTransform3D sourceTransform = new AffineTransform3D();
		source.getSourceTransform( t, level, sourceTransform );
		return sourceTransform;
	}


	public static RandomAccessibleInterval< ? > getRandomAccessibleInterval( Bdv bdv, int sourceId )
	{
		return bdv.getBdvHandle().getViewerPanel().getState().getSources().get( sourceId ).getSpimSource().getSource( 0, 0 );
	}

	public static RealRandomAccessible< ? > getRealRandomAccessible( Bdv bdv, int sourceId )
	{
		return bdv.getBdvHandle().getViewerPanel().getState().getSources().get( sourceId ).getSpimSource().getInterpolatedSource( 0, 0, Interpolation.NLINEAR );
	}

	public static void zoomToInterval( Bdv bdv, FinalInterval interval, double zoomFactor )
	{
		final AffineTransform3D affineTransform3D = getImageZoomTransform( bdv, interval, zoomFactor );

		bdv.getBdvHandle().getViewerPanel().setCurrentViewerTransform( affineTransform3D );
	}

	public static AffineTransform3D getImageZoomTransform( Bdv bdv, FinalInterval interval, double zoomFactor )
	{

		final AffineTransform3D affineTransform3D = new AffineTransform3D();

		double[] shiftToImage = new double[ 3 ];

		for ( int d = 0; d < 3; ++d )
		{
			shiftToImage[ d ] = -( interval.min( d ) + interval.dimension( d ) / 2.0 );
		}

		affineTransform3D.translate( shiftToImage );

		int[] bdvWindowDimensions = new int[ 2 ];
		bdvWindowDimensions[ 0 ] = bdv.getBdvHandle().getViewerPanel().getWidth();
		bdvWindowDimensions[ 1 ] = bdv.getBdvHandle().getViewerPanel().getHeight();

		affineTransform3D.scale( zoomFactor * bdvWindowDimensions[ 0 ] / interval.dimension( 0 ) );

		double[] shiftToBdvWindowCenter = new double[ 3 ];

		for ( int d = 0; d < 2; ++d )
		{
			shiftToBdvWindowCenter[ d ] += bdvWindowDimensions[ d ] / 2.0;
		}

		affineTransform3D.translate( shiftToBdvWindowCenter );

		return affineTransform3D;
	}


	public static RandomAccessibleInterval< IntegerType > getIndexImg( BdvStackSource bdvStackSource, int t, int level )
	{
		final Source source = getSource( bdvStackSource, 0 );

		return getIndexImg( source, t, level );

	}

	public static Source getSource( BdvStackSource bdvStackSource, int i )
	{
		final SourceAndConverter sourceAndConverter = ( SourceAndConverter ) bdvStackSource.getSources().get( 0 );

		return sourceAndConverter.getSpimSource();
	}

	public static RandomAccessibleInterval< IntegerType > getIndexImg( Source source, int t, int level )
	{
		if ( source instanceof TransformedSource )
		{
			final Source wrappedSource = ( ( TransformedSource ) source ).getWrappedSource();

			if ( wrappedSource instanceof LabelsSource )
			{
				return ( ( LabelsSource ) wrappedSource ).getWrappedSource( t, level );
			}
		}

		return null; // TODO: throw some type error...
	}

	public static ARGBType asArgbType( Color color )
	{
		return new ARGBType( ARGBType.rgba( color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() ) );
	}

	public static long[] getPositionInSourceStack( Source source, RealPoint mousePositionInMicrometerUnits, int t, int level )
	{
		final AffineTransform3D sourceTransform = BdvUtils.getSourceTransform( source, t, level );

		final RealPoint positionInPixelUnits = new RealPoint( 3 );

		sourceTransform.inverse().apply( mousePositionInMicrometerUnits, positionInPixelUnits );

		final long[] longPosition = new long[ 3 ];

		for ( int d = 0; d < 3; ++d )
		{
			longPosition[ d ] = (long) positionInPixelUnits.getFloatPosition( d );
		}

		return longPosition;
	}



	public static double[] getCurrentViewNormalVector( Bdv bdv )
	{

		AffineTransform3D currentViewerTransform = new AffineTransform3D();
		bdv.getBdvHandle().getViewerPanel().getState().getViewerTransform( currentViewerTransform );

		final double[] viewerC = new double[]{ 0, 0, 0 };
		final double[] viewerX = new double[]{ 1, 0, 0 };
		final double[] viewerY = new double[]{ 0, 1, 0 };

		final double[] dataC = new double[ 3 ];
		final double[] dataX = new double[ 3 ];
		final double[] dataY = new double[ 3 ];

		final double[] dataV1 = new double[ 3 ];
		final double[] dataV2 = new double[ 3 ];
		final double[] currentNormalVector = new double[ 3 ];

		currentViewerTransform.inverse().apply( viewerC, dataC );
		currentViewerTransform.inverse().apply( viewerX, dataX );
		currentViewerTransform.inverse().apply( viewerY, dataY );

		LinAlgHelpers.subtract( dataX, dataC, dataV1 );
		LinAlgHelpers.subtract( dataY, dataC, dataV2 );

		LinAlgHelpers.cross( dataV1, dataV2, currentNormalVector );

		LinAlgHelpers.normalize( currentNormalVector );

		return currentNormalVector;
	}


	public static void levelView( Bdv bdv, double[] targetNormalVector )
	{

		double[] currentNormalVector = BdvUtils.getCurrentViewNormalVector( bdv );

		AffineTransform3D currentViewerTransform = new AffineTransform3D();
		bdv.getBdvHandle().getViewerPanel().getState().getViewerTransform( currentViewerTransform );

		LinAlgHelpers.normalize( targetNormalVector ); // just to be sure.

		// determine rotation axis
		double[] rotationAxis = new double[ 3 ];
		LinAlgHelpers.cross( currentNormalVector, targetNormalVector, rotationAxis );
		if ( LinAlgHelpers.length( rotationAxis ) > 0 ) LinAlgHelpers.normalize( rotationAxis );

		// The rotation axis is in the coordinate system of the original data set => transform to viewer coordinate system
		double[] qCurrentRotation = new double[ 4 ];
		Affine3DHelpers.extractRotation( currentViewerTransform, qCurrentRotation );
		final AffineTransform3D currentRotation = quaternionToAffineTransform3D( qCurrentRotation );

		double[] rotationAxisInViewerSystem = new double[ 3 ];
		currentRotation.apply( rotationAxis, rotationAxisInViewerSystem );

		// determine rotation angle
		double angle = - Math.acos( LinAlgHelpers.dot( currentNormalVector, targetNormalVector ) );

		// construct rotation of angle around axis
		double[] rotationQuaternion = new double[ 4 ];
		LinAlgHelpers.quaternionFromAngleAxis( rotationAxisInViewerSystem, angle, rotationQuaternion );
		final AffineTransform3D rotation = quaternionToAffineTransform3D( rotationQuaternion );

		// apply transformation (rotating around current viewer centre position)
		final AffineTransform3D translateCenterToOrigin = new AffineTransform3D();
		translateCenterToOrigin.translate( DoubleStream.of( getBdvWindowCenter( bdv )).map( x -> -x ).toArray() );

		final AffineTransform3D translateCenterBack = new AffineTransform3D();
		translateCenterBack.translate( getBdvWindowCenter( bdv ) );

		ArrayList< AffineTransform3D > viewerTransforms = new ArrayList<>(  );

		viewerTransforms.add( currentViewerTransform.copy()
				.preConcatenate( translateCenterToOrigin )
				.preConcatenate( rotation )
				.preConcatenate( translateCenterBack )	);

		changeBdvViewerTransform( bdv, viewerTransforms, 2000 );

	}

	public static double[] getBdvWindowCenter( Bdv bdv )
	{
		final double[] centre = new double[ 3 ];

		centre[ 0 ] = bdv.getBdvHandle().getViewerPanel().getDisplay().getWidth() / 2.0;
		centre[ 1 ] = bdv.getBdvHandle().getViewerPanel().getDisplay().getHeight() / 2.0;

		return centre;
	}


	public static AffineTransform3D quaternionToAffineTransform3D( double[] rotationQuaternion )
	{
		double[][] rotationMatrix = new double[ 3 ][ 3 ];
		LinAlgHelpers.quaternionToR( rotationQuaternion, rotationMatrix );
		return matrixAsAffineTransform3D( rotationMatrix );
	}

	public static AffineTransform3D matrixAsAffineTransform3D( double[][] rotationMatrix )
	{
		final AffineTransform3D rotation = new AffineTransform3D();
		for ( int row = 0; row < 3; ++row )
			for ( int col = 0; col < 3; ++ col)
				rotation.set( rotationMatrix[ row ][ col ], row, col);
		return rotation;
	}

	public static void changeBdvViewerTransform(
			Bdv bdv,
			AffineTransform3D newViewerTransform,
			long duration)
	{

		AffineTransform3D currentViewerTransform = new AffineTransform3D();
		bdv.getBdvHandle().getViewerPanel().getState().getViewerTransform( currentViewerTransform );

		final SimilarityTransformAnimator similarityTransformAnimator =
				new SimilarityTransformAnimator(
						currentViewerTransform,
						newViewerTransform,
						0 ,
						0,
						duration );


		bdv.getBdvHandle().getViewerPanel().setTransformAnimator( similarityTransformAnimator );
		bdv.getBdvHandle().getViewerPanel().transformChanged( newViewerTransform );

	}


	public static void changeBdvViewerTransform(
			Bdv bdv,
			ArrayList< AffineTransform3D > transforms,
			long duration)
	{

		AffineTransform3D currentTransform = new AffineTransform3D();
		bdv.getBdvHandle().getViewerPanel().getState().getViewerTransform( currentTransform );

		ArrayList< SimilarityTransformAnimator > animators = new ArrayList<>(  );

		final SimilarityTransformAnimator firstAnimator =
				new SimilarityTransformAnimator(
						currentTransform.copy(),
						transforms.get( 0 ).copy(),
						0 ,
						0,
						duration );

		animators.add( firstAnimator );

		for ( int i = 1; i < transforms.size(); i++ )
		{
			final SimilarityTransformAnimator animator =
					new SimilarityTransformAnimator(
							transforms.get( i - 1 ).copy(),
							transforms.get( i ).copy(),
							0 ,
							0,
							duration );

			animators.add( animator );
		}


		AbstractTransformAnimator transformAnimator = new ConcatenatedTransformAnimator( duration, animators );

		bdv.getBdvHandle().getViewerPanel().setTransformAnimator( transformAnimator );
		//bdv.getBdvHandle().getViewerPanel().transformChanged( currentTransform.copy() );

	}

	public static < T extends RealType< T > & NativeType< T > > void showAsIJ1MultiColorImage( Bdv bdv, double resolution, ArrayList< RandomAccessibleInterval< T > > randomAccessibleIntervals )
	{
		final ImagePlus imp = ImageJFunctions.wrap( Views.stack( randomAccessibleIntervals ), "capture" );
		final ImagePlus dup = new Duplicator().run( imp ); // otherwise it is virtual and cannot be modified
		IJ.run( dup, "Subtract...", "value=32768 slice");
		VoxelDimensions voxelDimensions = getVoxelDimensions( bdv, 0 );
		IJ.run( dup, "Properties...", "channels="+randomAccessibleIntervals.size()+" slices=1 frames=1 unit="+voxelDimensions.unit()+" pixel_width="+resolution+" pixel_height="+resolution+" voxel_depth=1.0");
		final CompositeImage compositeImage = new CompositeImage( dup );
		for ( int channel = 1; channel <= compositeImage.getNChannels(); ++channel )
		{
			compositeImage.setC( channel );
			switch ( channel )
			{
				case 1: // tomogram
					compositeImage.setChannelLut( compositeImage.createLutFromColor( Color.GRAY ) );
					compositeImage.setDisplayRange( 0, 1000 ); // TODO: get from bdv
					break;
				case 2: compositeImage.setChannelLut( compositeImage.createLutFromColor( Color.GRAY ) ); break;
				case 3: compositeImage.setChannelLut( compositeImage.createLutFromColor( Color.RED ) ); break;
				case 4: compositeImage.setChannelLut( compositeImage.createLutFromColor( Color.GREEN ) ); break;
				default: compositeImage.setChannelLut( compositeImage.createLutFromColor( Color.BLUE ) ); break;
			}
		}
		compositeImage.show();
		compositeImage.setTitle( "capture" );
		IJ.run(compositeImage, "Make Composite", "");
	}

	public static ArrayList< Color > getColors( List< Integer > nonOverlaySources )
	{
		ArrayList< Color > defaultColors = new ArrayList<>(  );
		if ( nonOverlaySources.size() > 1 )
		{
			defaultColors.add( Color.BLUE );
			defaultColors.add( Color.GREEN );
			defaultColors.add( Color.RED );
			defaultColors.add( Color.MAGENTA );
			defaultColors.add( Color.GRAY );
			defaultColors.add( Color.GRAY );
			defaultColors.add( Color.GRAY );
			defaultColors.add( Color.GRAY );
			defaultColors.add( Color.GRAY );
		}
		else
		{
			defaultColors.add( Color.GRAY );
		}
		return defaultColors;
	}

	public static List< Integer > getNonOverlaySourceIndices( Bdv bdv, List< SourceState< ? > > sources )
	{
		final List< Integer > nonOverlaySources = new ArrayList<>(  );

		for ( int sourceIndex = 0; sourceIndex < sources.size(); ++sourceIndex )
		{
			String name = getName( bdv, sourceIndex );
			if ( ! name.contains( OVERLAY ) )
			{
				nonOverlaySources.add( sourceIndex );
			}
		}

		return nonOverlaySources;
	}

	public static boolean isLabelsSource( BdvStackSource bdvStackSource )
	{

		final Source source = getSource( bdvStackSource, 0 );

		return isLabelsSource( source );

	}

	private static boolean isLabelsSource( Source source )
	{
		if ( source instanceof TransformedSource )
		{
			final Source wrappedSource = ( ( TransformedSource ) source ).getWrappedSource();

			if ( wrappedSource instanceof LabelsSource )
			{
				return true;
			}
		}

		return false;
	}

	public static LabelsSource getLabelsSource( BdvStackSource bdvStackSource )
	{
		final Source source = getSource( bdvStackSource, 0 );

		return getLabelsSource( source );
	}

	private static LabelsSource getLabelsSource( Source source )
	{
		if ( source instanceof TransformedSource )
		{
			final Source wrappedSource = ( ( TransformedSource ) source ).getWrappedSource();

			if ( wrappedSource instanceof LabelsSource )
			{
				return  ( ( LabelsSource ) wrappedSource) ;
			}
		}

		return null;
	}



	public static RealPoint getGlobalMouseCoordinates( Bdv bdv )
	{
		final RealPoint posInBdvInMicrometer = new RealPoint( 3 );
		bdv.getBdvHandle().getViewerPanel().getGlobalMouseCoordinates( posInBdvInMicrometer );
		return posInBdvInMicrometer;
	}


	public static void deselectAllObjectsInActiveLabelSources( Bdv bdv )
	{
		final HashMap< Integer, Long > sourcesAndSelectedObjects = new HashMap<>();

		final List< Integer > visibleSourceIndices = bdv.getBdvHandle().getViewerPanel().getState().getVisibleSourceIndices();

		for ( int sourceIndex : visibleSourceIndices )
		{

			final SourceState< ? > sourceState = bdv.getBdvHandle().getViewerPanel().getState().getSources().get( sourceIndex );

			final Source source = sourceState.getSpimSource();

			if ( isLabelsSource( source ) )
			{

				BdvUtils.getLabelsSource( source ).selectNone( );
			}
		}

		bdv.getBdvHandle().getViewerPanel().requestRepaint();

	}

	public static Map< Integer, Long > selectObjectsInActiveLabelSources( Bdv bdv, RealPoint point )
	{

		final HashMap< Integer, Long > sourcesAndSelectedObjects = new HashMap<>();

		final List< Integer > visibleSourceIndices = bdv.getBdvHandle().getViewerPanel().getState().getVisibleSourceIndices();

		for ( int sourceIndex : visibleSourceIndices )
		{

			final SourceState< ? > sourceState = bdv.getBdvHandle().getViewerPanel().getState().getSources().get( sourceIndex );

			final Source source = sourceState.getSpimSource();

			if ( isLabelsSource( source ) )
			{
				final AffineTransform3D affineTransform3D = new AffineTransform3D();
				bdv.getBdvHandle().getViewerPanel().getState().getViewerTransform( affineTransform3D );
				int level = MipmapTransforms.getBestMipMapLevel( affineTransform3D, source, 0 );

				final RandomAccessibleInterval< IntegerType > indexImg = BdvUtils.getIndexImg( source, 0, level );

				final long[] positionInSourceStack = BdvUtils.getPositionInSourceStack( source, point, 0, level );

				final RandomAccess< IntegerType > access = indexImg.randomAccess();

				try
				{
					access.setPosition( positionInSourceStack );

					final long objectIndex = access.get().getIntegerLong();

					if ( objectIndex != 0 )
					{
						sourcesAndSelectedObjects.put( sourceIndex, objectIndex );
					}

					BdvUtils.getLabelsSource( source ).select( objectIndex );
				}
				catch ( Exception e )
				{
					int a = 1;
					// selected pixel outside image bounds
				}



			}
		}

		bdv.getBdvHandle().getViewerPanel().requestRepaint();

		return sourcesAndSelectedObjects;
	}


	public static void extractSelectedObject(
			final Bdv bdv,
			final RealPoint point,
			int level,
			final ArrayList< RandomAccessibleInterval< BitType > > masksOutput,
			final ArrayList< double[] > calibrationsOutput
	)
	{

		final List< Integer > visibleSourceIndices = bdv.getBdvHandle().getViewerPanel().getState().getVisibleSourceIndices();

		for ( int sourceIndex : visibleSourceIndices )
		{
			final SourceState< ? > sourceState = bdv.getBdvHandle().getViewerPanel().getState().getSources().get( sourceIndex );

			final Source source = sourceState.getSpimSource();

			if ( isLabelsSource( source ) )
			{
				level = level > source.getNumMipmapLevels() ?  source.getNumMipmapLevels() - 1 : level;

				//	int level = MipmapTransforms.getBestMipMapLevel( affineTransform3D, source, 0 );

				final RandomAccessibleInterval< IntegerType > indexImg = BdvUtils.getIndexImg( source, 0, level );

				final long[] positionInSourceStack = BdvUtils.getPositionInSourceStack( source, point, 0, level );

				final RegionExtractor regionExtractor = new RegionExtractor( indexImg, new DiamondShape( 1 ), 1000 * 1000 * 1000L );

				regionExtractor.run( positionInSourceStack );

				masksOutput.add( regionExtractor.getCroppedRegionMask() );

				calibrationsOutput.add( getCalibration( source, level ) );
			}
		}
	}

	public static double[] getCalibration( Source source, int level )
	{
		// TODO: is this logic correct?
		final AffineTransform3D sourceTransform = new AffineTransform3D();
		source.getSourceTransform( 0, level, sourceTransform );
		double[] transformedUnitVector = new double[ 3 ];
		AffineTransform3D transform3D = sourceTransform.copy();
		transform3D.setTranslation( new double[ 3 ] );
		transform3D.apply( new double[]{1,1,1}, transformedUnitVector );
		return transformedUnitVector;
	}

	public static void zoomToInterval( Bdv bdv, FinalRealInterval interval )
	{
		final AffineTransform3D affineTransform3D = getImageZoomTransform( bdv, interval );

		bdv.getBdvHandle().getViewerPanel().setCurrentViewerTransform( affineTransform3D );
	}

	public static AffineTransform3D getImageZoomTransform( Bdv bdv, FinalRealInterval interval  )
	{

		final AffineTransform3D affineTransform3D = new AffineTransform3D();

		double[] centerPosition = new double[ 3 ];

		for( int d = 0; d < 3; ++d )
		{
			final double center = ( interval.realMin( d ) + interval.realMax( d ) ) / 2.0;
			centerPosition[ d ] = - center;
		}

		affineTransform3D.translate( centerPosition );

		int[] bdvWindowDimensions = new int[ 2 ];
		bdvWindowDimensions[ 0 ] = bdv.getBdvHandle().getViewerPanel().getWidth();
		bdvWindowDimensions[ 1 ] = bdv.getBdvHandle().getViewerPanel().getHeight();

		final double intervalSize = interval.realMax( 0 ) - interval.realMin( 0 );
		affineTransform3D.scale(  1.0 * bdvWindowDimensions[ 0 ] / intervalSize );

		double[] shiftToBdvWindowCenter = new double[ 3 ];

		for( int d = 0; d < 2; ++d )
		{
			shiftToBdvWindowCenter[ d ] += bdvWindowDimensions[ d ] / 2.0;
		}

		affineTransform3D.translate( shiftToBdvWindowCenter );

		return affineTransform3D;
	}

	public static void centerBdvViewToPosition(  Bdv bdv, double[] position, double scale )
	{
		final AffineTransform3D newViewerTransform = getNewViewerTransform( bdv, position, scale );

		final double cX = 0; //- bdv.getBdvHandle().getViewerPanel().getDisplay().getWidth() / 2.0;
		final double cY = 0; //- bdv.getBdvHandle().getViewerPanel().getDisplay().getHeight() / 2.0;

		final AffineTransform3D currentViewerTransform = new AffineTransform3D();
		bdv.getBdvHandle().getViewerPanel().getState().getViewerTransform( currentViewerTransform );

		final SimilarityTransformAnimator similarityTransformAnimator =
				new SimilarityTransformAnimator( currentViewerTransform, newViewerTransform, cX ,cY, 3000 );

		bdv.getBdvHandle().getViewerPanel().setTransformAnimator( similarityTransformAnimator );
		bdv.getBdvHandle().getViewerPanel().transformChanged( currentViewerTransform );
	}


	public static AffineTransform3D getNewViewerTransform( Bdv bdv, double[] position, double scale )
	{
		final AffineTransform3D newViewerTransform = new AffineTransform3D();

		int[] bdvWindowDimensions = new int[ 3 ];
		bdvWindowDimensions[ 0 ] = bdv.getBdvHandle().getViewerPanel().getWidth();
		bdvWindowDimensions[ 1 ] = bdv.getBdvHandle().getViewerPanel().getHeight();

		double[] translation = new double[ 3 ];
		for( int d = 0; d < 3; ++d )
		{
			translation[ d ] = - position[ d ];
		}

		newViewerTransform.setTranslation( translation );
		newViewerTransform.scale( scale );

		double[] centerBdvWindowTranslation = new double[ 3 ];
		for( int d = 0; d < 3; ++d )
		{
			centerBdvWindowTranslation[ d ] = + bdvWindowDimensions[ d ] / 2.0;
		}

		newViewerTransform.translate( centerBdvWindowTranslation );

		return newViewerTransform;
	}
}
