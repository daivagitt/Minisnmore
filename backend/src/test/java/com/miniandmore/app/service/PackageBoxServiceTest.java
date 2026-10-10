package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.PackageBox;
import com.miniandmore.app.repository.PackageBoxRepository;
import com.miniandmore.app.service.dto.PackageBoxDTO;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PackageBoxServiceTest {

    @Mock
    private PackageBoxRepository packageBoxRepository;

    private PackageBoxService service;

    @BeforeEach
    void setUp() {
        service = new PackageBoxService(packageBoxRepository);
    }

    @Test
    void save_trimsNameAndPersistsDimensions() {
        PackageBoxDTO incoming = dto(null, "  Small carton  ", 20, 15, 10);
        when(packageBoxRepository.findFirstByNameIgnoreCase("Small carton")).thenReturn(Optional.empty());
        when(packageBoxRepository.save(any(PackageBox.class))).thenAnswer(invocation -> {
            PackageBox saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        PackageBoxDTO result = service.save(incoming);

        assertThat(result.getId()).isEqualTo(11L);
        assertThat(result.getName()).isEqualTo("Small carton");
        ArgumentCaptor<PackageBox> captor = ArgumentCaptor.forClass(PackageBox.class);
        verify(packageBoxRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Small carton");
        assertThat(captor.getValue().getLengthCm()).isEqualTo(20);
        assertThat(captor.getValue().getBreadthCm()).isEqualTo(15);
        assertThat(captor.getValue().getHeightCm()).isEqualTo(10);
        assertThat(captor.getValue().getWeightGrams()).isNull();
    }

    @Test
    void save_rejectsDuplicateName() {
        when(packageBoxRepository.findFirstByNameIgnoreCase("small carton")).thenReturn(
            Optional.of(new PackageBox().id(3L).name("Small carton"))
        );

        assertThatThrownBy(() -> service.save(dto(null, "small carton", 20, 15, 10)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        verify(packageBoxRepository, never()).save(any());
    }

    @Test
    void update_allowsSameNameOnSameRow() {
        PackageBox existing = new PackageBox().id(4L).name("Small carton").lengthCm(20).breadthCm(15).heightCm(10);
        when(packageBoxRepository.existsById(4L)).thenReturn(true);
        when(packageBoxRepository.findFirstByNameIgnoreCase("Small carton")).thenReturn(Optional.of(existing));
        when(packageBoxRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(packageBoxRepository.save(any(PackageBox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PackageBoxDTO result = service.update(dto(4L, "Small carton", 22, 16, 11));

        assertThat(result.getLengthCm()).isEqualTo(22);
        assertThat(result.getHeightCm()).isEqualTo(11);
    }

    @Test
    void findAllByName_returnsMappedRows() {
        when(packageBoxRepository.findAllByOrderByNameAsc()).thenReturn(
            List.of(new PackageBox().id(1L).name("Large").lengthCm(40).breadthCm(30).heightCm(20))
        );

        assertThat(service.findAllByName()).hasSize(1).first().satisfies(row -> {
            assertThat(row.getName()).isEqualTo("Large");
            assertThat(row.getBreadthCm()).isEqualTo(30);
        });
    }

    @Test
    void delete_missingRow_isNotFound() {
        when(packageBoxRepository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(9L))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static PackageBoxDTO dto(Long id, String name, int length, int breadth, int height) {
        PackageBoxDTO dto = new PackageBoxDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setLengthCm(length);
        dto.setBreadthCm(breadth);
        dto.setHeightCm(height);
        return dto;
    }
}
