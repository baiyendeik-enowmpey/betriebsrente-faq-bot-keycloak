package de.team.betriebsrente.rag;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;

class SeedDataInitializerTest {

    @Test
    void synchronisesBundledSeedFileAndBackfillsValidity() throws Exception {
        SeedSyncService seedSync = mock(SeedSyncService.class);
        VektorIndexService vektorIndex = mock(VektorIndexService.class);
        when(seedSync.synchronisiere(any(File.class))).thenReturn(SeedSyncService.Ergebnis.AKTUELL);

        ApplicationRunner runner = new SeedDataInitializer().seedOnEmpty(vektorIndex, seedSync);
        runner.run(new DefaultApplicationArguments());

        var reihenfolge = inOrder(vektorIndex, seedSync);
        reihenfolge.verify(vektorIndex).abgleichen();
        reihenfolge.verify(seedSync).synchronisiere(any(File.class));

        // Im Projektverzeichnis wird testdaten/fachwissen_seed.json gefunden.
        verify(seedSync).synchronisiere(new File("testdaten/fachwissen_seed.json"));
        verify(seedSync).ergaenzeFehlendeGueltigkeit();
    }

    @Test
    void exposesSeedImportFailures() throws Exception {
        SeedSyncService seedSync = mock(SeedSyncService.class);
        doThrow(new IllegalStateException("seed import failed")).when(seedSync).synchronisiere(any(File.class));

        ApplicationRunner runner = new SeedDataInitializer().seedOnEmpty(mock(VektorIndexService.class), seedSync);

        assertThrows(IllegalStateException.class, () -> runner.run(new DefaultApplicationArguments()));
    }
}
