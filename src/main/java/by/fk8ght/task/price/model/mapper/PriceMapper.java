package by.fk8ght.task.price.model.mapper;

import by.fk8ght.task.price.model.dto.PriceDTO;
import by.fk8ght.task.price.model.entity.PriceRecord;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PriceMapper {
    PriceDTO toDto(PriceRecord record);
}
