import bdv.tools.transformation.TransformedSource;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import de.embl.cba.bdv.utils.algorithms.RegionExtractor;
import de.embl.cba.bdv.utils.labels.ARGBConvertedRealTypeLabelsSource;
import de.embl.cba.bdv.utils.labels.LabelsSource;
import de.embl.cba.bdv.utils.transformhandlers.BehaviourTransformEventHandler3DGoogleMouse;
import ij.ImageJ;
import ij.ImagePlus;
import ij3d.Content;
import ij3d.Image3DUniverse;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.XmlIoSpimData;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.view.Views;
import org.scijava.vecmath.Color3f;

import java.io.File;

public class TestRegionExtraction
{

	public static void main( String[] args ) throws SpimDataException
	{
		final File file = new File( "/Users/tischer/Desktop/bdv_test_data/test.xml" );

		SpimData spimData = new XmlIoSpimData().load( file.toString() );

		final Source< VolatileARGBType > labelSource = new ARGBConvertedRealTypeLabelsSource( spimData, 0 );

		final BdvStackSource< VolatileARGBType > bdvStackSource =
				BdvFunctions.show( labelSource,
						BdvOptions.options().transformEventHandlerFactory( new BehaviourTransformEventHandler3DGoogleMouse.BehaviourTransformEventHandler3DFactory() ) );

		final SourceAndConverter< VolatileARGBType > sourceAndConverter = bdvStackSource.getSources().get( 0 );

		final Source< VolatileARGBType > spimSource = sourceAndConverter.getSpimSource();

		final Source wrappedSource = ( ( TransformedSource ) spimSource ).getWrappedSource();

		RandomAccessibleInterval source = ( ( LabelsSource ) wrappedSource ).getWrappedSource( 0,0 );

		final RegionExtractor regionExtractor = new RegionExtractor( source, new DiamondShape( 1 ), 1000*1000*1000L );

		regionExtractor.run( new long[]{ 35, 35, 58 } );

		if ( regionExtractor.isMaxRegionSizeReached( ) )
		{
			System.out.println( "MaxRegionSizeReached" );
		}

		RandomAccessibleInterval regionMask = regionExtractor.getCroppedRegionMask();

		new ImageJ();
		regionMask = Views.addDimension( regionMask, 0, 0 );
		regionMask = Views.permute( regionMask, 2,3 );
		final ImagePlus regionMaskImp = ImageJFunctions.show( regionMask );

		source = Views.addDimension( source, 0, 0 );
		source = Views.permute( source, 2,3 );
		ImageJFunctions.show( source );

		Image3DUniverse univ = new Image3DUniverse( );
		univ.show( );
		final Content content = univ.addMesh( regionMaskImp, null, "somename", 250, new boolean[]{ true, true, true }, 2 );
		content.setColor( new Color3f(0.5f, 0, 0.5f ) );

	}
}
