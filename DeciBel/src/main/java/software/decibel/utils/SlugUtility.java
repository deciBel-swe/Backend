package software.decibel.utils;

import java.util.function.Predicate;

public class SlugUtility {

  // Takes a title and a function that checks if a slug already exists (usually repo that has wants
  // to check if slug exists)
  // returns unique slug

  // predicate<string> -> function that takes string and returls bool
  public static String generateUniqueSlug(String title, Predicate<String> slugExists) {

    // convert title to be url friendly
    // any character not lower case or a digit replaced by -
    String baseTitle = title.toLowerCase().trim().replaceAll("[^a-z0-9]+", "-");

    String slug = baseTitle;
    int counter = 1;

    // keep on incrementing till unique
    while (slugExists.test(slug)) {
      slug = baseTitle + "-" + counter;
      counter++;
    }

    return slug;
  }
}
