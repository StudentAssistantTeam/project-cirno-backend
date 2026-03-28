package xyz.uthofficial.projectcirnobackend.entity;

import org.jetbrains.exposed.v1.sql.SqlExpressionBuilder;
import java.util.List;

public class TagReminder {

    public List<Tag> getAllTags() {
        return Tag.INSTANCE.all();
    }

    public void showAllTags() {
        List<Tag> tags = getAllTags();

             for (Tag tag : tags) {
            System.out.println(tag.getName()+" ");
        }
    }
}