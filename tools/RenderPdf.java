import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** PDF 한 쪽을 PNG 로 굽는다. 차트가 실제로 어떻게 찍혔는지 눈으로 보기 위한 도구. */
public class RenderPdf {

    public static void main(String[] args) throws Exception {
        Path pdf = Path.of(args[0]);
        int pageIndex = Integer.parseInt(args[1]);
        Path out = Path.of(args[2]);

        try (PDDocument document = Loader.loadPDF(Files.readAllBytes(pdf))) {
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, 120);
            ImageIO.write(image, "png", new File(out.toString()));
            System.out.println("pages=" + document.getNumberOfPages()
                    + " -> " + out + " (" + image.getWidth() + "x" + image.getHeight() + ")");
        }
    }
}
